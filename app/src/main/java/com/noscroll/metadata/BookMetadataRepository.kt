package com.noscroll.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        val cached = rawCached?.takeUnless { isLinkLike(it.author) || isLinkLike(it.title) }

        if (cached != null && cached.source != "manual") return@withContext cached
        if (cached != null && !onlineAllowed) return@withContext cached

        val embedded = PdfEmbeddedMetadata.extract(context, uri)
        val embeddedTitle = embedded?.title?.takeIf { it.isNotBlank() }
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
        val isbn = findIsbn(firstPagesText)
        if (onlineAllowed && isbn != null) {
            lookupByIsbn(key, isbn)?.let { entity ->
                return@withContext save(dao, entity)
            }
        }

        if (onlineAllowed) {
            val query = buildOnlineQuery(context, uri, embeddedTitle, embeddedAuthor, firstPagesText)
            lookupByQuery(key, query)?.let { entity ->
                return@withContext save(dao, entity)
            }
        }

        if (!embeddedTitle.isNullOrBlank() || !embeddedAuthor.isNullOrBlank()) {
            return@withContext save(
                dao = dao,
                entity = BookMetadataEntity(
                    bookUri = key,
                    title = embeddedTitle ?: fallbackTitle(uri),
                    author = embeddedAuthor ?: "Unknown Author",
                    source = "embedded_title_author",
                    confidence = 0.55f
                )
            )
        }

        val entity = cached ?: BookMetadataEntity(
            bookUri = key,
            title = fallbackTitle(uri),
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
                title = title.trim().ifBlank { fallbackTitle(uri) },
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
        dao.upsert(entity)
        return entity
    }

    private fun lookupByIsbn(bookUri: String, isbn: String): BookMetadataEntity? {
        OpenLibraryClient.lookupIsbn(isbn)?.let { result ->
            return BookMetadataEntity(
                bookUri = bookUri,
                title = result.title,
                author = result.author,
                isbn13 = result.isbn13,
                isbn10 = result.isbn10,
                source = "isbn_open_library",
                confidence = 0.92f,
                coverUrl = result.coverUrl,
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        GoogleBooksClient.search("isbn:$isbn")?.let { result ->
            return BookMetadataEntity(
                bookUri = bookUri,
                title = result.title,
                author = result.author,
                isbn13 = result.isbn13 ?: isbn.takeIf { it.length == 13 },
                isbn10 = result.isbn10 ?: isbn.takeIf { it.length == 10 },
                source = "isbn_google_books",
                confidence = 0.9f,
                coverUrl = result.coverUrl,
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        return null
    }

    private fun lookupByQuery(bookUri: String, query: String): BookMetadataEntity? {
        GoogleBooksClient.search(query)?.let { result ->
            return BookMetadataEntity(
                bookUri = bookUri,
                title = result.title,
                author = result.author,
                isbn13 = result.isbn13,
                isbn10 = result.isbn10,
                source = "google_books",
                confidence = 0.48f,
                coverUrl = result.coverUrl,
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        OpenLibraryClient.search(query)?.let { result ->
            return BookMetadataEntity(
                bookUri = bookUri,
                title = result.title,
                author = result.author,
                isbn13 = result.isbn13,
                isbn10 = result.isbn10,
                source = "open_library",
                confidence = 0.45f,
                coverUrl = result.coverUrl,
                lastLookupAtMillis = System.currentTimeMillis()
            )
        }
        return null
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

    private fun fallbackTitle(uri: Uri): String {
        val raw = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.', missingDelimiterValue = uri.lastPathSegment ?: "Untitled")
            ?: "Untitled"
        return Uri.decode(raw).replace('_', ' ').ifBlank { "Untitled" }
    }

    private suspend fun buildOnlineQuery(
        context: Context,
        uri: Uri,
        embeddedTitle: String?,
        embeddedAuthor: String?,
        firstPagesText: String
    ): String {
        val filename = fallbackTitle(uri)
        val ocrText = if (firstPagesText.isBlank()) {
            renderCoverPage(context, uri)?.let { bitmap ->
                try {
                    CoverPageOcr.extractText(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }.orEmpty()
        } else {
            ""
        }
        val coverSignal = titleLikeLines(firstPagesText.ifBlank { ocrText }).joinToString(" ").take(150)
        return listOf(embeddedTitle, embeddedAuthor, filename, coverSignal)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .take(180)
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
