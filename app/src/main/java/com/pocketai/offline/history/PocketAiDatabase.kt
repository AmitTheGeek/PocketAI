package com.pocketai.offline.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedSummaryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PocketAiDatabase : RoomDatabase() {
    abstract fun savedSummaryDao(): SavedSummaryDao
}

