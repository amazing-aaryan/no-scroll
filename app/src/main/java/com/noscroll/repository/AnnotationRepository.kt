package com.noscroll.repository

import android.content.Context
import com.noscroll.data.AnnotationDatabase
import com.noscroll.data.AnnotationEntity

object AnnotationRepository {
    suspend fun upsert(context: Context, noteText: String, highlightId: Long) {
        AnnotationDatabase.getInstance(context).annotationDao()
            .upsert(AnnotationEntity(highlightId = highlightId, noteText = noteText))
    }

    suspend fun get(context: Context, highlightId: Long): AnnotationEntity? =
        AnnotationDatabase.getInstance(context).annotationDao().getForHighlight(highlightId)

    suspend fun delete(context: Context, highlightId: Long) =
        AnnotationDatabase.getInstance(context).annotationDao().deleteForHighlight(highlightId)
}
