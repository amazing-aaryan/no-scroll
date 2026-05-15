package com.noscroll.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quote_cards",
    indices = [Index(value = ["bookUri"]), Index(value = ["createdAtMillis"])]
)
data class QuoteCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val highlightId: Long?,
    val quoteText: String,
    val pageIndex: Int,
    val themeName: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

