package com.noscroll.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HighlightEntity::class, AnnotationEntity::class, BookMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AnnotationDatabase : RoomDatabase() {
    abstract fun highlightDao(): HighlightDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookMetadataDao(): BookMetadataDao

    companion object {
        @Volatile private var instance: AnnotationDatabase? = null

        fun getInstance(context: Context): AnnotationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnnotationDatabase::class.java,
                    "annotations.db"
                ).build().also { instance = it }
            }
    }
}
