package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteCardDao {
    @Query("SELECT * FROM quote_cards ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<QuoteCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuoteCardEntity): Long

    @Query("DELETE FROM quote_cards WHERE id = :id")
    suspend fun delete(id: Long)
}

