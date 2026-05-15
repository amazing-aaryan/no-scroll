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
        val rawCached = dao.get(key)
        val onlineAllowed = allowOnlineOnce || MetadataLookupPrefs.isOnlineLookupEnabled(context)
        // Discard cached entries where author/title is an API key or URL (stale bad data).
        val cached = rawCached?.takeUnless {
            isLinkLike(it.author) || isLinkLike(it.title) || isBadMetadataText(it.title)
        }

        val cachedNeedsUpgrade = cached != null && (
            cached.author == "Unknown Author" ||
                cached.source == "manual" ||
                cached.source == "cover_ocr" ||
                cached.confidence < 0.7f
            )
        if (cached != null && onlineAllowed && cached.author == "Unknown Author") {
            lookupAuthorByTitle(key, cached.title)?.let { enriched ->
                return@withContext save(
                    dao,
                    cached.copy(
                        title = cleanBookTitle(enriched.title).ifBlank { cleanBookTitle(cached.title) },
                        author = enriched.author,
                        isbn13 = cached.isbn13 ?: enriched.isbn13,
                        isbn10 = cached.isbn10 ?: enriched.isbn10,
                        source = "${cached.source}+cached_title_author",
                        confidence = (cached.confidence + 0.28f).coerceAtMost(0.9f),
                        coverUrl = cached.coverUrl ?: enriched.coverUrl,
                        lastLookupAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }
        if (cached != null && (!onlineAllowed || !cachedNeedsUpgrade)) return@withContext cached

        val embedded = PdfEmbeddedMetadata.extract(context, uri)
        val embeddedTitle = embedded?.title?.takeIf { it.isNotBlank() && !isBadMetadataText(it) }
        val embeddedAuthor = embedded?.author?.takeIf { it.isNotBlank() }
        if (!embeddedTitle.isNullOrBlank() && !embeddedAuthor.isNullOrBlank() && !onlineAllowed) {
            return@withContext save(
                dao = dao,
                entity = BookMetadataEntity(
                    bookUri = key,
                    title = embeddedTitle,
                    author = embeddedAuthor,
                    source = "embedded_title_author",
                    confidence = 0.72f
                )
            )
        }

        val firstPagesText = document?.let { extractFirstPagesText(it) }.orEmpty()
        val coverOcrText = if (onlineAllowed || firstPagesText.isBlank()) extractCoverOcrText(context, uri) else ""
        val availableText = firstPagesText.ifBlank { coverOcrText }
        if (!onlineAllowed && embeddedTitle.isNullOrBlank()) {
            inferOfflineMetadata(key, availableText)?.let { entity ->
                return@withContext save(dao, entity)
            }
        }
        val isbn = findIsbn(availableText)
        if (onlineAllowed && isbn != null) {
            lookupByIsbn(key, isbn)?.let { entity ->
                return@withContext save(dao, entity)
            }
        }

        if (onlineAllowed) {
            val query = buildOnlineQuery(context, uri, embeddedTitle, embeddedAuthor, firstPagesText, coverOcrText)
            lookupByQuery(key, query, availableText)?.let { entity ->
                return@withContext save(dao, entity)
            }
        }

        if (!embeddedTitle.isNullOrBlank() || !embeddedAuthor.isNullOrBlank()) {
            return@withContext save(
                dao = dao,
                entity = BookMetadataEntity(
                    bookUri = key,
                    title = embeddedTitle ?: fallbackTitle(context, uri),
                    author = embeddedAuthor ?: "Unknown Author",
                    source = "embedded_title_author",
                    confidence = 0.55f
                )
            )
        }

        val entity = cached ?: BookMetadataEntity(
            bookUri = key,
            title = fallbackTitle(context, uri),
            author = "Unknown Author",
            source = "manual",
            confidence = 0.2f
        )
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

    private suspend fun save(
        dao: com.noscroll.data.BookMetadataDao,
        entity: BookMetadataEntity
    ): BookMetadataEntity {
        val normalized = applyKnownMetadata(entity.copy(title = cleanBookTitle(entity.title)))
        dao.upsert(normalized)
        return normalized
    }

    private fun lookupByIsbn(bookUri: String, isbn: String): BookMetadataEntity? {
        val candidates = listOfNotNull(
            OpenLibraryClient.lookupIsbn(isbn)?.toEntity(bookUri, "isbn_open_library", 0.92f),
            GoogleBooksClient.search("isbn:$isbn")?.toEntity(
                bookUri = bookUri,
                source = "isbn_google_books",
                confidence = 0.9f,
                fallbackIsbn = isbn
            )
        )
        return chooseBestCandidate(candidates)
    }

    private fun lookupByQuery(bookUri: String, query: String, ocrText: String): BookMetadataEntity? {
        val candidates = listOfNotNull(
            GoogleBooksClient.search(query)?.toEntity(bookUri, "google_books", 0.48f),
            OpenLibraryClient.search(query)?.toEntity(bookUri, "open_library", 0.45f)
        )
        val best = chooseBestCandidate(candidates) ?: return null
        if (best.author != "Unknown Author") return best

        lookupAuthorByTitle(bookUri, best.title)?.let { enriched ->
            return best.copy(
                title = cleanBookTitle(enriched.title).ifBlank { cleanBookTitle(best.title) },
                author = enriched.author,
                isbn13 = best.isbn13 ?: enriched.isbn13,
                isbn10 = best.isbn10 ?: enriched.isbn10,
                source = "${best.source}+title_author",
                confidence = (best.confidence + 0.22f).coerceAtMost(0.82f),
                coverUrl = best.coverUrl ?: enriched.coverUrl,
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        extractAuthorFromOcr(ocrText, best.title)?.let { ocrAuthor ->
            return best.copy(
                title = cleanBookTitle(best.title),
                author = ocrAuthor,
                source = "${best.source}+cover_author",
                confidence = (best.confidence + 0.16f).coerceAtMost(0.74f),
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        return best
    }

    private fun lookupAuthorByTitle(bookUri: String, title: String): BookMetadataEntity? {
        val cleanTitle = cleanBookTitle(title)
        val titleQuery = "\"$cleanTitle\""
        val candidates = listOfNotNull(
            GoogleBooksClient.search("intitle:$cleanTitle")?.toEntity(bookUri, "google_books_title", 0.7f),
            GoogleBooksClient.search(titleQuery)?.toEntity(bookUri, "google_books_exact", 0.64f),
            OpenLibraryClient.search(titleQuery)?.toEntity(bookUri, "open_library_title", 0.66f),
            OpenLibraryClient.search(cleanTitle)?.toEntity(bookUri, "open_library_plain_title", 0.58f)
        ).filter { it.author != "Unknown Author" && titlesMatch(cleanTitle, it.title) }
        return chooseBestCandidate(candidates)
    }

    private fun chooseBestCandidate(candidates: List<BookMetadataEntity>): BookMetadataEntity? =
        candidates.maxWithOrNull(
            compareBy<BookMetadataEntity> { if (it.author != "Unknown Author") 1 else 0 }
                .thenBy { it.confidence }
                .thenBy { if (it.coverUrl.isNullOrBlank()) 0 else 1 }
        )

    private fun GoogleBooksResult.toEntity(
        bookUri: String,
        source: String,
        confidence: Float,
        fallbackIsbn: String? = null
    ): BookMetadataEntity =
        BookMetadataEntity(
            bookUri = bookUri,
            title = cleanBookTitle(title),
            author = author,
            isbn13 = isbn13 ?: fallbackIsbn?.takeIf { it.length == 13 },
            isbn10 = isbn10 ?: fallbackIsbn?.takeIf { it.length == 10 },
            source = source,
            confidence = if (author == "Unknown Author") confidence - 0.12f else confidence,
            coverUrl = coverUrl,
            lastLookupAtMillis = System.currentTimeMillis()
        )

    private fun OpenLibraryResult.toEntity(
        bookUri: String,
        source: String,
        confidence: Float
    ): BookMetadataEntity =
        BookMetadataEntity(
            bookUri = bookUri,
            title = cleanBookTitle(title),
            author = author,
            isbn13 = isbn13,
            isbn10 = isbn10,
            source = source,
            confidence = if (author == "Unknown Author") confidence - 0.12f else confidence,
            coverUrl = coverUrl,
            lastLookupAtMillis = System.currentTimeMillis()
        )

    private fun titlesMatch(expected: String, actual: String): Boolean {
        val left = normalizeTitle(expected).split(" ").filter { it.length > 2 }.toSet()
        val right = normalizeTitle(actual).split(" ").filter { it.length > 2 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return false
        val smaller = if (left.size <= right.size) left else right
        val larger  = if (left.size <= right.size) right else left
        return smaller.count { it in larger }.toFloat() / smaller.size >= 0.5f
    }

    private fun normalizeTitle(title: String): String =
        cleanBookTitle(title).lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\b(temp|pdf|ebook|edition)\\b"), " ")
            .trim()

    private fun cleanBookTitle(title: String): String =
        title
            .replace(Regex("\\s+temp$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+pdf$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun applyKnownMetadata(entity: BookMetadataEntity): BookMetadataEntity = entity

    private fun inferOfflineMetadata(bookUri: String, text: String): BookMetadataEntity? {
        val lines = titleLikeLines(text)
            .filterNot { it.equals("about the author", ignoreCase = true) }
            .filterNot { it.startsWith("copyright", ignoreCase = true) }
        val title = lines.firstOrNull { line ->
            val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            words.size in 2..5 &&
                !line.contains(",") &&
                !line.startsWith("an ", ignoreCase = true) &&
                !line.startsWith("a ", ignoreCase = true) &&
                !line.contains(".com", ignoreCase = true) &&
                !isLikelyPersonName(line)
        }?.takeUnless { isBadMetadataText(it) }
            ?: lines.firstOrNull()?.takeUnless { isBadMetadataText(it) }
            ?: return null
        val author = lines.drop(1).firstOrNull { line ->
            line.length in 3..60 &&
                !line.any { it.isDigit() } &&
                !line.contains(".com", ignoreCase = true) &&
                isLikelyPersonName(line)
        } ?: "Unknown Author"
        return BookMetadataEntity(
            bookUri = bookUri,
            title = title,
            author = author,
            source = "cover_ocr",
            confidence = if (author == "Unknown Author") 0.35f else 0.5f
        )
    }

    private suspend fun extractFirstPagesText(document: PdfDocument): String {
        val pageCount = document.pageCount.coerceAtMost(5)
        return buildString {
            for (page in 0 until pageCount) {
                try {
                    document.getPageContent(page)
                        ?.textContents
                        ?.forEach { appendLine(it.text) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun findIsbn(text: String): String? {
        val regex = Regex("""ISBN(?:-1[03])?:?\s*((?:97[89][-\s]?)?\d[-\s]?\d{2,5}[-\s]?\d{2,7}[-\s]?[\dXx])""")
        return regex.findAll(text)
            .map { it.groupValues[1].filter { char -> char.isDigit() || char == 'X' || char == 'x' }.uppercase() }
            .firstOrNull { it.length == 10 || it.length == 13 }
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
        val decoded = Uri.decode(raw)
            .substringBefore('?')
            .substringBeforeLast('.', missingDelimiterValue = Uri.decode(raw).substringBefore('?'))
            .replace('_', ' ')
            .trim()
        return decoded.takeUnless { it.contains(";") || it.contains("=") || it.length > 80 }
            ?.ifBlank { null }
            ?: "Untitled PDF"
    }

    private suspend fun buildOnlineQuery(
        context: Context,
        uri: Uri,
        embeddedTitle: String?,
        embeddedAuthor: String?,
        firstPagesText: String,
        coverOcrText: String
    ): String {
        val filename = fallbackTitle(context, uri)
        val coverSignal = titleLikeLines(firstPagesText.ifBlank { coverOcrText }).joinToString(" ").take(150)
        return listOf(embeddedTitle, embeddedAuthor, filename, coverSignal)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .take(180)
    }

    private suspend fun extractCoverOcrText(context: Context, uri: Uri): String =
        renderCoverPage(context, uri)?.let { bitmap ->
            try {
                CoverPageOcr.extractText(bitmap)
            } finally {
                bitmap.recycle()
            }
        }.orEmpty()

    private fun extractAuthorFromOcr(text: String, title: String): String? {
        val titleTokens = normalizeTitle(title).split(" ").filter { it.isNotBlank() }.toSet()
        return text.lines()
            .map { it.trim().removePrefix("by ").removePrefix("By ") }
            .filter { it.length in 3..60 }
            .filterNot { line ->
                val normalized = normalizeTitle(line)
                normalized.isBlank() ||
                    normalized.split(" ").any { it in titleTokens } ||
                    normalized.contains("author") ||
                    normalized.contains("copyright") ||
                    normalized.contains("edition") ||
                    normalized.contains("new york times") ||
                    line.any { it.isDigit() }
            }
            .firstOrNull { isLikelyPersonName(it) }
    }

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

    private fun isLinkLike(s: String): Boolean =
        s.startsWith("/") || s.startsWith("http", ignoreCase = true)

    private fun isBadMetadataText(s: String): Boolean =
        s.isBlank() ||
            s.contains(";") ||
            s.contains("=") ||
            s.length > 80 ||
            s.endsWith(" temp", ignoreCase = true)

    private fun isLikelyPersonName(line: String): Boolean {
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size in 2..4 && words.all { word ->
            word.firstOrNull()?.isUpperCase() == true &&
                word.drop(1).all { it.isLowerCase() || it == '.' || it == '-' || it == '\'' }
        }
    }

    private fun titleLikeLines(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { line ->
                line.length in 3..80 &&
                    !line.contains("@") &&
                    !line.startsWith("http", ignoreCase = true)
            }
            .take(8)
}
