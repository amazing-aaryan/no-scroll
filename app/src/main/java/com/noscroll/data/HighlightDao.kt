package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookUri = :uri ORDER BY pageIndex, createdAtMillis")
    suspend fun getForBook(uri: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookUri = :uri AND pageIndex = :page ORDER BY createdAtMillis")
    suspend fun getForPage(uri: String, page: Int): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)
}
