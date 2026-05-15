package com.noscroll.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.noscroll.PdfLibraryAdapter
import com.noscroll.PdfStorage
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.BookEntity
import kotlinx.coroutines.flow.Flow

object BookRepository {
    fun observeBooks(context: Context): Flow<List<BookEntity>> =
        AnnotationDatabase.getInstance(context).bookDao().observeAll()

    suspend fun migrateLegacyLibrary(context: Context) {
        val dao = AnnotationDatabase.getInstance(context).bookDao()
        PdfStorage.getLibrary(context).forEachIndexed { index, entry ->
            val existing = dao.get(entry.uri)
            if (existing == null) {
                val now = System.currentTimeMillis() - index
                val name = cleanDisplayName(resolveName(context, Uri.parse(entry.uri)))
                    .ifBlank { cleanDisplayName(entry.displayName) }
                dao.upsert(
                    BookEntity(
                        bookUri = entry.uri,
                        displayName = name,
                        addedAtMillis = now,
                        lastOpenedAtMillis = 0,
                        fileSizeBytes = resolveSize(context, Uri.parse(entry.uri))
                    )
                )
            } else if (isBadDisplayName(existing.displayName)) {
                dao.upsert(existing.copy(displayName = cleanDisplayName(resolveName(context, Uri.parse(entry.uri)))))
            }
        }
        PdfStorage.getSavedUri(context)?.let { legacy ->
            val uri = legacy.toString()
            if (dao.get(uri) == null) {
                dao.upsert(
                    BookEntity(
                        bookUri = uri,
                        displayName = cleanDisplayName(resolveName(context, legacy)),
                        addedAtMillis = System.currentTimeMillis(),
                        lastOpenedAtMillis = 0,
                        lastPageIndex = PdfStorage.getSavedPage(context),
                        fileSizeBytes = resolveSize(context, legacy)
                    )
                )
            }
        }
    }

    suspend fun importBook(context: Context, uri: Uri, displayName: String) {
        val uriString = uri.toString()
        val now = System.currentTimeMillis()
        AnnotationDatabase.getInstance(context).bookDao().upsert(
            BookEntity(
                bookUri = uriString,
                displayName = cleanDisplayName(displayName).ifBlank { cleanDisplayName(resolveName(context, uri)) },
                addedAtMillis = now,
                lastOpenedAtMillis = now,
                fileSizeBytes = resolveSize(context, uri)
            )
        )
        PdfStorage.addToLibrary(context, uriString, cleanDisplayName(displayName))
    }

    suspend fun openBook(context: Context, uri: String) {
        val dao = AnnotationDatabase.getInstance(context).bookDao()
        val current = dao.get(uri)
        if (current != null) {
            dao.upsert(current.copy(lastOpenedAtMillis = System.currentTimeMillis()))
        }
        PdfStorage.setSelected(context, uri)
    }

    suspend fun updateProgress(context: Context, uri: String, pageIndex: Int, pageCount: Int) {
        AnnotationDatabase.getInstance(context)
            .bookDao()
            .updateProgress(uri, pageIndex, pageCount, System.currentTimeMillis())
    }

    suspend fun setFavorite(context: Context, uri: String, favorite: Boolean) {
        AnnotationDatabase.getInstance(context).bookDao().setFavorite(uri, favorite)
    }

    suspend fun delete(context: Context, uri: String) {
        AnnotationDatabase.getInstance(context).bookDao().delete(uri)
        PdfStorage.removeFromLibrary(context, uri)
    }

    private fun resolveName(context: Context, uri: Uri): String =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment ?: "Unknown PDF"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Unknown PDF"
        }

    private fun resolveSize(context: Context, uri: Uri): Long =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }

    fun cleanDisplayName(raw: String): String {
        val decoded = Uri.decode(raw)
        val base = decoded
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBeforeLast('.', missingDelimiterValue = decoded.substringAfterLast('/').substringBefore('?'))
            .replace('_', ' ')
            .replace(Regex("\\s+temp$", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (base.isBlank() || base.contains(";") || base.contains("=") || base.length > 80) {
            "Untitled PDF"
        } else {
            PdfLibraryAdapter.prettifyName(base)
        }
    }

    private fun isBadDisplayName(value: String): Boolean =
        value.isBlank() || value.contains(";") || value.contains("=") || value.length > 80 || value == "Unknown PDF"
            || value.endsWith(" temp", ignoreCase = true)
}
