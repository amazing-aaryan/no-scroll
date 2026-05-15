package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAtMillis DESC, addedAtMillis DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE bookUri = :uri LIMIT 1")
    suspend fun get(uri: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY addedAtMillis DESC")
    suspend fun getAllOnce(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE bookUri = :uri")
    suspend fun delete(uri: String)

    @Query(
        "UPDATE books SET lastPageIndex = :pageIndex, pageCount = :pageCount, " +
            "lastOpenedAtMillis = :openedAtMillis WHERE bookUri = :uri"
    )
    suspend fun updateProgress(uri: String, pageIndex: Int, pageCount: Int, openedAtMillis: Long)

    @Query("UPDATE books SET isFavorite = :favorite WHERE bookUri = :uri")
    suspend fun setFavorite(uri: String, favorite: Boolean)
}
