package com.pocketai.offline

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.pocketai.offline.history.PocketAiDatabase
import com.pocketai.offline.history.RoomSummaryHistoryRepository
import com.pocketai.offline.history.SummaryHistoryRepository
import com.pocketai.offline.inference.LlamaCppSummarizationEngine
import com.pocketai.offline.inference.SummarizationEngine

class PocketAiApplication : Application() {
    val container: PocketAiContainer by lazy {
        PocketAiContainer(this)
    }
}

class PocketAiContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val database: PocketAiDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            PocketAiDatabase::class.java,
            "pocketai.db"
        ).build()
    }

    val historyRepository: SummaryHistoryRepository by lazy {
        RoomSummaryHistoryRepository(database.savedSummaryDao())
    }

    val summarizationEngine: SummarizationEngine by lazy {
        LlamaCppSummarizationEngine(appContext)
    }
}

