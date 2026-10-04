package com.pocketai.offline.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSummaryDao {
    @Insert
    suspend fun insert(summary: SavedSummaryEntity): Long

    @Query("SELECT * FROM saved_summaries ORDER BY created_at_epoch_ms DESC, id DESC")
    fun observeAll(): Flow<List<SavedSummaryEntity>>

    @Query("SELECT * FROM saved_summaries WHERE id = :id")
    fun observeById(id: Long): Flow<SavedSummaryEntity?>

    @Query("DELETE FROM saved_summaries WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

