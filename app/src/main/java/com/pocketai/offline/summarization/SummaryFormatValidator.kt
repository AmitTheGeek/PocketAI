package com.pocketai.offline.summarization

data class SummaryFormatValidation(
    val isValid: Boolean,
    val bulletCount: Int,
    val reason: String? = null,
)

object SummaryFormatValidator {
    private val bulletPattern = Regex("""^\s*(?:[-*]|\u2022|\u2023|\u2013|\u2014|\d{1,2}[.)])\s+(\S.*)$""")

    fun validate(summary: String): SummaryFormatValidation {
        val lines = summary
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return SummaryFormatValidation(
                isValid = false,
                bulletCount = 0,
                reason = "Expected 1-3 bullet points, but the model returned no bullet items."
            )
        }

        val bulletCount = lines.count { bulletPattern.matches(it) }
        val firstProseLine = lines.firstOrNull { !bulletPattern.matches(it) }
        if (firstProseLine != null) {
            return SummaryFormatValidation(
                isValid = false,
                bulletCount = bulletCount,
                reason = "Expected only bullet points, but the model included extra prose."
            )
        }

        if (bulletCount !in 1..3) {
            return SummaryFormatValidation(
                isValid = false,
                bulletCount = bulletCount,
                reason = "Expected 1-3 bullet points, but the model returned $bulletCount."
            )
        }

        return SummaryFormatValidation(isValid = true, bulletCount = bulletCount)
    }
}
