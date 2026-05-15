package com.noscroll.repository

import android.content.Context
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.AnnotationEntity
import com.noscroll.data.BookEntity
import com.noscroll.data.BookMetadataEntity
import com.noscroll.data.BookmarkEntity
import com.noscroll.data.HighlightEntity
import com.noscroll.data.QuoteCardEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class NotebookState(
    val books: List<BookEntity> = emptyList(),
    val metadata: List<BookMetadataEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val annotations: List<AnnotationEntity> = emptyList(),
    val quotes: List<QuoteCardEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList()
)

object NotebookRepository {
    fun observe(context: Context): Flow<NotebookState> {
        val db = AnnotationDatabase.getInstance(context)
        return combine(
            combine(
                db.bookDao().observeAll(),
                db.bookMetadataDao().observeAll(),
                db.highlightDao().observeAll()
            ) { books, metadata, highlights ->
                Triple(books, metadata, highlights)
            },
            combine(
                db.annotationDao().observeAll(),
                db.quoteCardDao().observeAll(),
                db.bookmarkDao().observeAll()
            ) { annotations, quotes, bookmarks ->
                Triple(annotations, quotes, bookmarks)
            }
        ) { left, right ->
            NotebookState(
                books = left.first,
                metadata = left.second,
                highlights = left.third,
                annotations = right.first,
                quotes = right.second,
                bookmarks = right.third
            )
        }
    }
}
