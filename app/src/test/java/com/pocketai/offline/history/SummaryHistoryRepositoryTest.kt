package com.pocketai.offline.history

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SummaryHistoryRepositoryTest {
    private lateinit var database: PocketAiDatabase
    private lateinit var repository: SummaryHistoryRepository

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(app, PocketAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomSummaryHistoryRepository(database.savedSummaryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadRoundTripPreservesSourceOutputAndWarningMetadata() = runBlocking {
        val id = repository.save(
            NewSavedSummary(
                sourceText = "Original source",
                summaryText = "- Final summary",
                createdAtEpochMs = 1_000L,
                durationMs = 4_800L,
                refinementOccurred = true,
                formatWarning = "Format warning",
                modelName = "qwen.gguf",
                modelSizeBytes = 1_234L
            )
        )

        val saved = repository.observeSummary(id).first()

        requireNotNull(saved)
        assertEquals(id, saved.id)
        assertEquals("Original source", saved.sourceText)
        assertEquals("- Final summary", saved.summaryText)
        assertEquals(1_000L, saved.createdAtEpochMs)
        assertEquals(4_800L, saved.durationMs)
        assertEquals(true, saved.refinementOccurred)
        assertEquals("Format warning", saved.formatWarning)
        assertEquals("qwen.gguf", saved.modelName)
        assertEquals(1_234L, saved.modelSizeBytes)
    }

    @Test
    fun summariesAreNewestFirstWithIdTieBreakerForEqualTimestamps() = runBlocking {
        val older = repository.save(sampleSummary(source = "older", createdAt = 500L))
        val firstSameTime = repository.save(sampleSummary(source = "first", createdAt = 1_000L))
        val secondSameTime = repository.save(sampleSummary(source = "second", createdAt = 1_000L))

        val ids = repository.observeSummaries().first().map { it.id }

        assertEquals(listOf(secondSameTime, firstSameTime, older), ids)
    }

    @Test
    fun deleteRemovesSavedSummary() = runBlocking {
        val id = repository.save(sampleSummary())

        repository.delete(id)

        assertNull(repository.observeSummary(id).first())
        assertEquals(emptyList<SavedSummary>(), repository.observeSummaries().first())
    }

    private fun sampleSummary(
        source: String = "source",
        createdAt: Long = 1_000L,
    ): NewSavedSummary =
        NewSavedSummary(
            sourceText = source,
            summaryText = "- $source summary",
            createdAtEpochMs = createdAt,
            durationMs = 4_000L,
            refinementOccurred = false,
            formatWarning = null,
            modelName = "qwen.gguf",
            modelSizeBytes = 1_234L
        )
}

