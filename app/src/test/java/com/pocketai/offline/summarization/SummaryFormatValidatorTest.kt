package com.pocketai.offline.summarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryFormatValidatorTest {
    @Test
    fun acceptsOneToThreeBulletItemsWithCommonMarkers() {
        val summary = """
            - First item
            * Second item
            1. Third item
        """.trimIndent()

        val result = SummaryFormatValidator.validate(summary)

        assertTrue(result.isValid)
        assertEquals(3, result.bulletCount)
        assertNull(result.reason)
    }

    @Test
    fun acceptsUnicodeBulletMarkers() {
        val summary = """
            ${"\u2022"} First item
            ${"\u2013"} Second item
        """.trimIndent()

        val result = SummaryFormatValidator.validate(summary)

        assertTrue(result.isValid)
        assertEquals(2, result.bulletCount)
    }

    @Test
    fun rejectsExtraProse() {
        val summary = """
            Summary:
            - First item
        """.trimIndent()

        val result = SummaryFormatValidator.validate(summary)

        assertFalse(result.isValid)
        assertEquals(1, result.bulletCount)
        assertTrue(result.reason!!.contains("extra prose"))
    }

    @Test
    fun rejectsMoreThanThreeBulletsWithoutTruncating() {
        val summary = """
            - One
            - Two
            - Three
            - Four
        """.trimIndent()

        val result = SummaryFormatValidator.validate(summary)

        assertFalse(result.isValid)
        assertEquals(4, result.bulletCount)
        assertTrue(result.reason!!.contains("1-3"))
    }

    @Test
    fun rejectsEmptyBulletText() {
        val summary = """
            -
            - Real item
        """.trimIndent()

        val result = SummaryFormatValidator.validate(summary)

        assertFalse(result.isValid)
        assertEquals(1, result.bulletCount)
    }
}
