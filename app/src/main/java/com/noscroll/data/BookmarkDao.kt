package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookUri = :uri ORDER BY pageIndex")
    fun observeForBook(uri: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookUri = :uri AND pageIndex = :pageIndex LIMIT 1")
    suspend fun get(uri: String, pageIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE bookUri = :uri AND pageIndex = :pageIndex")
    suspend fun delete(uri: String, pageIndex: Int)
}

