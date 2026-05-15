package com.noscroll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE highlightId = :highlightId LIMIT 1")
    suspend fun getForHighlight(highlightId: Long): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE highlightId = :highlightId")
    suspend fun deleteForHighlight(highlightId: Long)
}
