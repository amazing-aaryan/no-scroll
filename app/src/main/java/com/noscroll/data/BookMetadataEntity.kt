package com.noscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_metadata")
data class BookMetadataEntity(
    @PrimaryKey val bookUri: String,
    val title: String,
    val author: String,
    val source: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
