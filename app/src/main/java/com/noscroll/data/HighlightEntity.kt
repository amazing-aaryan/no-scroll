package com.noscroll.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    indices = [Index(value = ["bookUri", "pageIndex"])]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageIndex: Int,
    val startCharIndex: Int,
    val endCharIndex: Int,
    val quoteText: String,
    val selectionBoundsJson: String = "",
    val colorArgb: Int,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
