package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookMetadataDao {
    @Query("SELECT * FROM book_metadata")
    fun observeAll(): Flow<List<BookMetadataEntity>>

    @Query("SELECT * FROM book_metadata WHERE bookUri = :uri LIMIT 1")
    suspend fun get(uri: String): BookMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookMetadataEntity)
}
