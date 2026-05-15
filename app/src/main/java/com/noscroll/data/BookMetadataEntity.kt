package com.noscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_metadata")
data class BookMetadataEntity(
    @PrimaryKey val bookUri: String,
    val title: String,
    val author: String,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val source: String,
    val confidence: Float = 0f,
    val coverUrl: String? = null,
    val lastLookupAtMillis: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
