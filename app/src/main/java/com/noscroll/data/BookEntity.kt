package com.noscroll.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["lastOpenedAtMillis"]),
        Index(value = ["displayName"]),
        Index(value = ["collectionId"])
    ]
)
data class BookEntity(
    @PrimaryKey val bookUri: String,
    val displayName: String,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val lastPageIndex: Int = 0,
    val pageCount: Int = 0,
    val fileSizeBytes: Long = 0,
    val isFavorite: Boolean = false,
    val collectionId: Long? = null
)

