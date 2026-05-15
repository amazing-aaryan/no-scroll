package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookCollectionDao {
    @Query("SELECT * FROM book_collections ORDER BY name")
    fun observeAll(): Flow<List<BookCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(collection: BookCollectionEntity): Long

    @Query("DELETE FROM book_collections WHERE id = :id")
    suspend fun delete(id: Long)
}

