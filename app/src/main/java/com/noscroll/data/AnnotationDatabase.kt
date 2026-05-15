package com.noscroll.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HighlightEntity::class,
        AnnotationEntity::class,
        BookMetadataEntity::class,
        BookEntity::class,
        BookCollectionEntity::class,
        BookmarkEntity::class,
        QuoteCardEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AnnotationDatabase : RoomDatabase() {
    abstract fun highlightDao(): HighlightDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun bookMetadataDao(): BookMetadataDao
    abstract fun bookDao(): BookDao
    abstract fun bookCollectionDao(): BookCollectionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun quoteCardDao(): QuoteCardDao

    companion object {
        @Volatile private var instance: AnnotationDatabase? = null

        fun getInstance(context: Context): AnnotationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnnotationDatabase::class.java,
                    "annotations.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE highlights ADD COLUMN selectionBoundsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE highlights ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_metadata ADD COLUMN isbn13 TEXT")
                db.execSQL("ALTER TABLE book_metadata ADD COLUMN isbn10 TEXT")
                db.execSQL("ALTER TABLE book_metadata ADD COLUMN confidence REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_metadata ADD COLUMN coverUrl TEXT")
                db.execSQL("ALTER TABLE book_metadata ADD COLUMN lastLookupAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS books (
                        bookUri TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        addedAtMillis INTEGER NOT NULL,
                        lastOpenedAtMillis INTEGER NOT NULL,
                        lastPageIndex INTEGER NOT NULL DEFAULT 0,
                        pageCount INTEGER NOT NULL DEFAULT 0,
                        fileSizeBytes INTEGER NOT NULL DEFAULT 0,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        collectionId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_lastOpenedAtMillis ON books(lastOpenedAtMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_displayName ON books(displayName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_collectionId ON books(collectionId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        colorArgb INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookUri TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_bookUri_pageIndex ON bookmarks(bookUri, pageIndex)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quote_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookUri TEXT NOT NULL,
                        highlightId INTEGER,
                        quoteText TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        themeName TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quote_cards_bookUri ON quote_cards(bookUri)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quote_cards_createdAtMillis ON quote_cards(createdAtMillis)")
            }
        }
    }
}
