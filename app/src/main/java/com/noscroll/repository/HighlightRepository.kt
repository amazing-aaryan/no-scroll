package com.noscroll.repository

import android.content.Context
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.HighlightEntity

object HighlightRepository {
    suspend fun save(context: Context, entity: HighlightEntity): Long =
        AnnotationDatabase.getInstance(context).highlightDao().insert(entity)

    suspend fun getForPage(context: Context, uri: String, page: Int): List<HighlightEntity> =
        AnnotationDatabase.getInstance(context).highlightDao().getForPage(uri, page)

    suspend fun getForBook(context: Context, uri: String): List<HighlightEntity> =
        AnnotationDatabase.getInstance(context).highlightDao().getForBook(uri)

    suspend fun delete(context: Context, id: Long) =
        AnnotationDatabase.getInstance(context).highlightDao().deleteById(id)

    suspend fun updateColor(context: Context, id: Long, color: Int) =
        AnnotationDatabase.getInstance(context).highlightDao().updateColor(id, color)
}
