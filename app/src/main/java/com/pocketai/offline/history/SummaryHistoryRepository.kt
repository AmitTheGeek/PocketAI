package com.pocketai.offline.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SummaryHistoryRepository {
    fun observeSummaries(): Flow<List<SavedSummary>>
    fun observeSummary(id: Long): Flow<SavedSummary?>
    suspend fun save(summary: NewSavedSummary): Long
    suspend fun delete(id: Long)
}

class RoomSummaryHistoryRepository(
    private val dao: SavedSummaryDao,
) : SummaryHistoryRepository {
    override fun observeSummaries(): Flow<List<SavedSummary>> =
        dao.observeAll().map { rows -> rows.map { it.toSavedSummary() } }

    override fun observeSummary(id: Long): Flow<SavedSummary?> =
        dao.observeById(id).map { it?.toSavedSummary() }

    override suspend fun save(summary: NewSavedSummary): Long =
        dao.insert(summary.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}

