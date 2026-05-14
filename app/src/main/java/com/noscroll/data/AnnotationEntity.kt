package com.noscroll.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = HighlightEntity::class,
            parentColumns = ["id"],
            childColumns = ["highlightId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["highlightId"], unique = true)]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val highlightId: Long,
    val noteText: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
