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
        allowOnlineOnce: Boolean = false
    ): BookMetadataEntity = withContext(Dispatchers.IO) {
        val key = uri.toString()
        val dao = AnnotationDatabase.getInstance(context).bookMetadataDao()
        val cached = dao.get(key)

        if (cached?.source == "manual") return@withContext cached
        if (cached?.source == "vision_ai") return@withContext cached
        // Return good cached result; re-run pipeline if author is unknown
        if (cached != null && cached.author != "Unknown Author" &&
            !isLinkLike(cached.author) && !isLinkLike(cached.title)
        ) return@withContext cached
        // Delete stale bad row so upsert below writes a fresh result
        if (cached != null && cached.author == "Unknown Author" && allowOnlineOnce) {
            dao.delete(key)
        }

        // Groq Vision — send cover image to LLaMA 3.2 Vision, best accuracy
        if (allowOnlineOnce) {
            val coverBitmap = renderCoverPage(context, uri)
            if (coverBitmap != null) {
                try {
                    val groq = GroqVisionClient.identify(coverBitmap)
                    if (groq != null && groq.title.isNotBlank()) {
                        val entity = BookMetadataEntity(
                            bookUri = key,
                            title = groq.title,
                            author = groq.author,
                            source = "vision_ai",
                            confidence = 0.95f
                        )
                        dao.upsert(entity)
                        return@withContext entity
                    }
                } finally {
                    coverBitmap.recycle()
                }
            }
        }

        val ocrText = extractCoverOcrText(context, uri)
        val fallback = fallbackTitle(context, uri)
        var entity = extractFromCoverOcr(key, ocrText, fallback)

        if (allowOnlineOnce &&
            entity.title != "Untitled PDF" && !isLinkLike(entity.title) &&
            (entity.author == "Unknown Author" || entity.confidence < 0.75f)
        ) {
            entity = networkEnrich(entity) ?: entity
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

    private fun extractFromCoverOcr(bookUri: String, ocrText: String, fallbackStr: String): BookMetadataEntity {
        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { line ->
                line.length in 2..80 &&
                line.any { it.isLetter() } &&
                !line.contains('©') &&
                !line.contains("ISBN", ignoreCase = true) &&
                !line.contains("www.", ignoreCase = true) &&
                !line.startsWith("http", ignoreCase = true) &&
                !line.contains('@') &&
                !line.contains("copyright", ignoreCase = true) &&
                !line.contains("published", ignoreCase = true) &&
                !line.contains("all rights", ignoreCase = true)
            }

        if (lines.isEmpty()) return BookMetadataEntity(
            bookUri = bookUri, title = fallbackStr, author = "Unknown Author",
            source = "cover_ocr", confidence = 0.2f
        )

        // Explicit "by / edited by / written by" pattern
        val byPattern = Regex("""^(?:by|edited by|written by)\s+(.+)""", RegexOption.IGNORE_CASE)
        var explicitAuthor: String? = null
        var byIdx = -1
        for ((i, line) in lines.withIndex()) {
            val m = byPattern.matchEntire(line) ?: continue
            val candidate = m.groupValues[1].trim()
            if (candidate.length in 2..60 && candidate.none { it.isDigit() }) {
                explicitAuthor = candidate
                byIdx = i
                break
            }
        }

        // Title: first substantive line — not the by-line, not publisher noise,
        // not a person name, reasonable word count
        val titleLine = lines
            .filterIndexed { i, _ -> i != byIdx }
            .firstOrNull { line ->
                val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                words.size in 1..8 &&
                !isLikelyPersonName(line) &&
                !isPublisherNoise(line) &&
                !isBadText(line)
            } ?: lines.firstOrNull()

        val title = titleLine?.let { cleanTitle(it) }
            ?.takeIf { !isBadText(it) }
            ?: fallbackStr

        // Author: explicit "by X" match, then first person-like name that is not the title line
        val author = explicitAuthor
            ?: lines.filter { it != titleLine }
                .firstOrNull { line -> isLikelyPersonName(line) && !isPublisherNoise(line) }
            ?: "Unknown Author"

        return BookMetadataEntity(
            bookUri = bookUri,
            title = title,
            author = author,
            source = "cover_ocr",
            confidence = if (author != "Unknown Author") 0.7f else 0.4f
        )
    }

    private fun isPublisherNoise(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("publisher") || lower.contains("publishing") ||
            lower.contains(" press") || lower.contains("bestseller") ||
            lower.contains("new york times") || lower.contains("international") ||
            lower.contains("award") || lower.contains("national book") ||
            lower.contains("magazine") || lower.contains("review") ||
            lower.contains("foreword") || lower.contains("preface")
    }

    private fun isLikelyPersonName(line: String): Boolean {
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size in 2..4 && words.all { word ->
            word.firstOrNull()?.isUpperCase() == true &&
                word.drop(1).all { it.isLowerCase() || it == '.' || it == '-' || it == '\'' }
        }
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\s+temp$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+pdf$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun isBadText(s: String): Boolean =
        s.isBlank() || s.contains(';') || s.contains('=') || s.length > 80 ||
            s.endsWith(" temp", ignoreCase = true)

    private fun networkEnrich(entity: BookMetadataEntity): BookMetadataEntity? {
        val queryTitle = entity.title.takeIf { !isLinkLike(it) && it != "Untitled PDF" } ?: return null

        // 1. Exact intitle: match — highest precision
        val googleExact = try { GoogleBooksClient.search("intitle:\"$queryTitle\"") } catch (_: Exception) { null }
        if (googleExact != null && googleExact.author != "Unknown Author") {
            return entity.copy(
                title = if (entity.title == "Untitled PDF") googleExact.title else entity.title,
                author = googleExact.author,
                source = "network",
                confidence = 0.85f
            )
        }

        // 2. Broader Google Books query — catches titles with slight OCR noise
        val googleBroad = try { GoogleBooksClient.search(queryTitle) } catch (_: Exception) { null }
        if (googleBroad != null && googleBroad.author != "Unknown Author") {
            return entity.copy(
                title = googleBroad.title,
                author = googleBroad.author,
                source = "network",
                confidence = 0.80f
            )
        }

        // 3. Open Library fallback
        val ol = try { OpenLibraryClient.search(queryTitle) } catch (_: Exception) { null }
        if (ol != null && ol.author != "Unknown Author") {
            return entity.copy(
                title = if (entity.title == "Untitled PDF") ol.title else entity.title,
                author = ol.author,
                source = "network",
                confidence = 0.80f
            )
        }

        return null
    }

    private fun isLinkLike(s: String): Boolean =
        s.startsWith("/") || s.startsWith("http", ignoreCase = true)

    private suspend fun extractCoverOcrText(context: Context, uri: Uri): String =
        renderCoverPage(context, uri)?.let { bitmap ->
            try { CoverPageOcr.extractText(bitmap) } finally { bitmap.recycle() }
        }.orEmpty()

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
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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
