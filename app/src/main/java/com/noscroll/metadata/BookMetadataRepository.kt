package com.noscroll.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.BookMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookMetadataRepository {
    suspend fun resolve(context: Context, uri: Uri, allowOnlineOnce: Boolean = false): BookMetadataEntity =
        withContext(Dispatchers.IO) {
            val key = uri.toString()
            val dao = AnnotationDatabase.getInstance(context).bookMetadataDao()
            val cached = dao.get(key)
            if (cached != null && cached.source != "manual") return@withContext cached
            if (cached != null && !allowOnlineOnce && !MetadataLookupPrefs.isOnlineLookupEnabled(context)) {
                return@withContext cached
            }

            val onlineAllowed = allowOnlineOnce || MetadataLookupPrefs.isOnlineLookupEnabled(context)
            if (onlineAllowed) {
                val query = buildOnlineQuery(context, uri)
                GoogleBooksClient.search(query)?.let { result ->
                    val entity = BookMetadataEntity(
                        bookUri = key,
                        title = result.title,
                        author = result.author,
                        source = "google_books"
                    )
                    dao.upsert(entity)
                    return@withContext entity
                }
            }

            val entity = cached ?: BookMetadataEntity(
                bookUri = key,
                title = fallbackTitle(uri),
                author = "Unknown Author",
                source = "manual"
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
                source = "manual"
            )
            AnnotationDatabase.getInstance(context).bookMetadataDao().upsert(entity)
            entity
        }

    private fun fallbackTitle(uri: Uri): String {
        val raw = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.', missingDelimiterValue = uri.lastPathSegment ?: "Untitled")
            ?: "Untitled"
        return Uri.decode(raw).replace('_', ' ').ifBlank { "Untitled" }
    }

    private suspend fun buildOnlineQuery(context: Context, uri: Uri): String {
        val filename = fallbackTitle(uri)
        val ocrText = renderCoverPage(context, uri)?.let { bitmap ->
            try {
                CoverPageOcr.extractText(bitmap)
            } finally {
                bitmap.recycle()
            }
        }.orEmpty()
        val coverSignal = titleLikeLines(ocrText).joinToString(" ").take(150)
        return listOf(filename, coverSignal)
            .filter { it.isNotBlank() }
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
