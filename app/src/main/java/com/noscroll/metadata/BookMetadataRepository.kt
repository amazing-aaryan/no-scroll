package com.noscroll.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.pdf.PdfDocument
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.BookMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookMetadataRepository {

    suspend fun resolve(
        context: Context,
        uri: Uri,
        document: PdfDocument? = null,
        allowOnlineOnce: Boolean = false,
        coverBitmap: Bitmap? = null,
        forceOnline: Boolean = false
    ): BookMetadataEntity = withContext(Dispatchers.IO) {
        val key = uri.toString()
        val dao = AnnotationDatabase.getInstance(context).bookMetadataDao()
        val cached = dao.get(key)

        android.util.Log.d("NoScrollMeta", "resolve: cached=${cached?.source} allowOnline=$allowOnlineOnce force=$forceOnline")
        if (!forceOnline && cached?.source == "manual") return@withContext cached
        if (!forceOnline && cached != null && cached.author != "Unknown Author" &&
            !isLinkLike(cached.author) && !isLinkLike(cached.title)
        ) {
            android.util.Log.d("NoScrollMeta", "returning good cache: title='${cached.title}' author='${cached.author}'")
            return@withContext cached
        }
        if (cached != null && (forceOnline || cached.author == "Unknown Author")) {
            dao.delete(key)
        }

        // 1. Block-level cover OCR
        val blocks: List<CoverBlock> = if (coverBitmap != null) {
            try {
                val b = CoverPageOcr.recognizeBlocks(coverBitmap)
                android.util.Log.d("NoScrollMeta", "recognizeBlocks(bitmap): ${b.size} blocks: ${b.take(5).map { "'${it.text.take(40)}' area=${it.area}" }}")
                b
            } catch (e: Exception) {
                android.util.Log.e("NoScrollMeta", "recognizeBlocks FAILED: ${e.message}")
                emptyList()
            }
        } else {
            android.util.Log.d("NoScrollMeta", "no coverBitmap — rendering internally")
            renderCoverPage(context, uri)?.let { bmp ->
                try {
                    val b = CoverPageOcr.recognizeBlocks(bmp)
                    android.util.Log.d("NoScrollMeta", "recognizeBlocks(internal): ${b.size} blocks")
                    b
                } finally { bmp.recycle() }
            } ?: emptyList()
        }

        val fallback = fallbackTitle(context, uri)
        var entity = extractFromBlocks(key, blocks, fallback)
        android.util.Log.d("NoScrollMeta", "extractFromBlocks: title='${entity.title}' author='${entity.author}' conf=${entity.confidence}")

        // 2. Network enrich via Google Books / Open Library
        if ((allowOnlineOnce || forceOnline) &&
            entity.title != "Untitled PDF" && !isLinkLike(entity.title) &&
            (entity.author == "Unknown Author" || entity.confidence < 0.75f)
        ) {
            android.util.Log.d("NoScrollMeta", "networkEnrich: querying '${entity.title}'")
            val enriched = networkEnrich(entity)
            android.util.Log.d("NoScrollMeta", "networkEnrich result: ${if (enriched != null) "author='${enriched.author}'" else "null"}")
            entity = enriched ?: entity
        } else {
            android.util.Log.d("NoScrollMeta", "networkEnrich skipped: allowOnline=$allowOnlineOnce author='${entity.author}' conf=${entity.confidence}")
        }

        dao.upsert(entity)
        entity
    }

    suspend fun saveManual(context: Context, uri: Uri, title: String, author: String): BookMetadataEntity =
        withContext(Dispatchers.IO) {
            val entity = BookMetadataEntity(
                bookUri = uri.toString(),
                title = title.trim().ifBlank { fallbackTitle(context, uri) },
                author = author.trim().ifBlank { "Unknown Author" },
                source = "manual",
                confidence = 1f
            )
            AnnotationDatabase.getInstance(context).bookMetadataDao().upsert(entity)
            entity
        }

    // Title = largest prominent block that is NOT a person name.
    // Author = explicit "by X" match or largest block that IS a person name.
    private fun extractFromBlocks(bookUri: String, blocks: List<CoverBlock>, fallbackStr: String): BookMetadataEntity {
        if (blocks.isEmpty()) return BookMetadataEntity(
            bookUri = bookUri, title = fallbackStr, author = "Unknown Author",
            source = "cover_ocr", confidence = 0.2f
        )

        val cleaned = blocks.filter { block ->
            !isPublisherNoise(block.text) &&
            !isBadText(block.text) &&
            block.text.any { it.isLetter() } &&
            !block.text.contains("ISBN", ignoreCase = true) &&
            !block.text.contains("www.", ignoreCase = true) &&
            !block.text.startsWith("http", ignoreCase = true) &&
            !block.text.contains('@') &&
            !block.text.contains('©')
        }

        if (cleaned.isEmpty()) return BookMetadataEntity(
            bookUri = bookUri, title = fallbackStr, author = "Unknown Author",
            source = "cover_ocr", confidence = 0.2f
        )

        // Explicit "by X" / "edited by X" takes priority as author signal
        val byPattern = Regex("""^(?:by|edited by|written by)\s+(.+)""", RegexOption.IGNORE_CASE)
        val explicitAuthor = cleaned
            .flatMap { it.text.split(Regex("""\s{2,}""")) }
            .map { it.trim() }
            .firstNotNullOfOrNull { segment ->
                byPattern.matchEntire(segment)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { it.length in 2..60 && it.none { c -> c.isDigit() } }
            }

        // Title = merge all large short non-person-name blocks (handles split covers like "ATOMIC" + "HABITS")
        val nonPersonBlocks = cleaned.filter { block ->
            val words = block.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            words.size in 1..10 && !isLikelyPersonName(block.text) && !isBadText(block.text)
        }
        val largestArea = nonPersonBlocks.maxOfOrNull { it.area } ?: 0
        val titleThreshold = largestArea * 0.6
        val titleComponents = nonPersonBlocks
            .filter { it.area >= titleThreshold }
            .filter { block -> block.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size <= 4 }
            .sortedBy { it.centerYFraction }
        val title = if (titleComponents.isNotEmpty()) {
            cleanTitle(titleComponents.joinToString(" ") { toTitleCase(it.text) })
        } else {
            nonPersonBlocks.firstOrNull()?.let { cleanTitle(toTitleCase(it.text)) } ?: fallbackStr
        }

        // Author = explicit match or largest person-name block (excluding title components)
        val titleTexts = titleComponents.map { it.text }.toSet()
        val authorBlock = if (explicitAuthor == null) {
            cleaned.filter { it.text !in titleTexts }.firstOrNull { isLikelyPersonName(it.text) }
        } else null
        val author = explicitAuthor ?: authorBlock?.text ?: "Unknown Author"

        return BookMetadataEntity(
            bookUri = bookUri,
            title = title,
            author = author,
            source = "cover_ocr",
            confidence = if (author != "Unknown Author") 0.7f else 0.4f
        )
    }

    private fun networkEnrich(entity: BookMetadataEntity): BookMetadataEntity? {
        val queryTitle = entity.title.takeIf { !isLinkLike(it) && it != "Untitled PDF" } ?: return null

        val googleExact = try { GoogleBooksClient.search("intitle:\"$queryTitle\"") } catch (_: Exception) { null }
        if (googleExact != null && googleExact.author != "Unknown Author") {
            return entity.copy(
                title = googleExact.title,
                author = googleExact.author,
                source = "network",
                confidence = 0.85f
            )
        }

        val googleBroad = try { GoogleBooksClient.search(queryTitle) } catch (_: Exception) { null }
        if (googleBroad != null && googleBroad.author != "Unknown Author") {
            return entity.copy(
                title = googleBroad.title,
                author = googleBroad.author,
                source = "network",
                confidence = 0.80f
            )
        }

        val ol = try { OpenLibraryClient.search(queryTitle) } catch (_: Exception) { null }
        if (ol != null && ol.author != "Unknown Author") {
            return entity.copy(
                title = ol.title,
                author = ol.author,
                source = "network",
                confidence = 0.80f
            )
        }

        return null
    }

    private fun isPublisherNoise(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("publisher") || lower.contains("publishing") ||
            lower.contains(" press") || lower.contains("bestseller") ||
            lower.contains("new york times") || lower.contains("international") ||
            lower.contains("award") || lower.contains("national book") ||
            lower.contains("magazine") || lower.contains("review") ||
            lower.contains("foreword") || lower.contains("preface") ||
            lower.contains("copyright") || lower.contains("all rights") ||
            lower.contains("published")
    }

    private fun isLikelyPersonName(text: String): Boolean {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size in 2..4 && words.all { word ->
            word.firstOrNull()?.isUpperCase() == true &&
                word.drop(1).all { it.isLowerCase() || it == '.' || it == '-' || it == '\'' }
        }
    }

    private fun toTitleCase(text: String): String =
        text.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\s+temp$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+pdf$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun isBadText(s: String): Boolean =
        s.isBlank() || s.contains(';') || s.contains('=') || s.length > 120 ||
            s.endsWith(" temp", ignoreCase = true)

    private fun isLinkLike(s: String): Boolean =
        s.startsWith("/") || s.startsWith("http", ignoreCase = true)

    private fun renderCoverPage(context: Context, uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            renderer.openPage(0).use { page ->
                val width = 1080
                val scale = width.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun fallbackTitle(context: Context, uri: Uri): String {
        val displayName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
        }
        val raw = displayName ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.', missingDelimiterValue = uri.lastPathSegment ?: "Untitled")
            ?: "Untitled"
        val decoded = Uri.decode(raw).substringBefore('?')
            .substringBeforeLast('.', missingDelimiterValue = Uri.decode(raw).substringBefore('?'))
            .replace('_', ' ').trim()
        return decoded
            .takeUnless { it.contains(';') || it.contains('=') || it.length > 80 }
            ?.ifBlank { null }
            ?: "Untitled PDF"
    }
}
