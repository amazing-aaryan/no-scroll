package com.noscroll.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_collections")
data class BookCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val colorArgb: Int = 0xFF77846F.toInt()
)

