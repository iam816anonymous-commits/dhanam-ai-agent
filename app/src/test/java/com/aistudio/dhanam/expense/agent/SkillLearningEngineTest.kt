package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.ExpenseEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillLearningEngineTest {
    private fun row(id: Long, title: String, category: String, date: LocalDate) = ExpenseEntity(
        id = id,
        title = title,
        amountPaise = 25_000L,
        category = category,
        occurredOnEpochDay = date.toEpochDay(),
        note = "",
        createdAtEpochMillis = id,
        updatedAtEpochMillis = id
    )

    @Test fun proposesOnlyRepeatedConsistentDescriptionsWithSuccessfulHoldoutTests() {
        val rows = listOf(
            row(1, "Metro Card", "Transport", LocalDate.of(2026, 8, 1)),
            row(2, "metro card", "Transport", LocalDate.of(2026, 8, 15)),
            row(3, "Metro Card", "Transport", LocalDate.of(2026, 9, 1)),
            row(4, "Metro Card", "Transport", LocalDate.of(2026, 10, 1))
        )
        val candidate = SkillLearningEngine.propose(rows).single()
        assertEquals("metro card", candidate.matchText)
        assertEquals("Transport", candidate.category)
        assertEquals(4, candidate.exampleCount)
        assertEquals(4, candidate.testPassCount)
        assertEquals(4, candidate.testTotalCount)
        assertEquals(64, candidate.skillKey.length)
    }

    @Test fun rejectsDescriptionsWithConflictingHistoricalCategories() {
        val rows = listOf(
            row(1, "Transfer", "Transport", LocalDate.of(2026, 8, 1)),
            row(2, "Transfer", "Bills", LocalDate.of(2026, 8, 15)),
            row(3, "Transfer", "Food", LocalDate.of(2026, 9, 1)),
            row(4, "Transfer", "Transport", LocalDate.of(2026, 10, 1))
        )
        assertTrue(SkillLearningEngine.propose(rows).isEmpty())
    }

    @Test fun doesNotLearnFromFewerThanThreeDistinctDates() {
        val date = LocalDate.of(2026, 10, 1)
        val rows = listOf(
            row(1, "Bus pass", "Transport", date),
            row(2, "Bus pass", "Transport", date),
            row(3, "Bus pass", "Transport", date)
        )
        assertTrue(SkillLearningEngine.propose(rows).isEmpty())
    }
}
