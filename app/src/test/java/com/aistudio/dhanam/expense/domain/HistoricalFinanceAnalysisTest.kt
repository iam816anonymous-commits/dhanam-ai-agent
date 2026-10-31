package com.aistudio.dhanam.expense.domain

import com.aistudio.dhanam.expense.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoricalFinanceAnalysisTest {
    private fun row(amount: Long, category: String, date: LocalDate) = ExpenseEntity(
        title = "record", amountPaise = amount, category = category,
        occurredOnEpochDay = date.toEpochDay(), note = "",
        createdAtEpochMillis = 1L, updatedAtEpochMillis = 1L
    )

    @Test fun reportsCalendarAveragesAndYearTotalsFromImportedHistory() {
        val rows = listOf(
            row(10_000L, "Food", LocalDate.of(2025, 1, 1)),
            row(20_000L, "Travel", LocalDate.of(2026, 1, 1))
        )
        val result = HistoricalFinanceAnalysis.summarize(rows)
        assertNotNull(result)
        assertEquals(366L, result!!.coveredDays)
        assertEquals(53L, result.coveredWeeks)
        assertEquals(13L, result.coveredMonths)
        assertEquals(2L, result.coveredYears)
        assertEquals(30_000L, result.totalSpentPaise)
        assertEquals(2, result.yearlyTotals.size)
        assertEquals(10_000L, result.yearlyTotals.first().amountPaise)
        assertEquals(20_000L, result.yearlyTotals.last().amountPaise)
    }

    @Test fun adaptiveBudgetsUsePreviousMonthAndTenPercentBufferRoundedToHundredRupees() {
        val rows = listOf(
            row(123_456L, "Food", LocalDate.of(2026, 9, 4)),
            row(50_000L, "Food", LocalDate.of(2026, 9, 12)),
            row(200_000L, "Transport", LocalDate.of(2026, 9, 14)),
            row(999_999L, "Shopping", LocalDate.of(2026, 10, 1))
        )
        val result = HistoricalFinanceAnalysis.adaptiveCategoryBudgets(rows, YearMonth.of(2026, 10))
        assertEquals(200_000L, result["Food"])
        assertEquals(220_000L, result["Transport"])
        assertNull(result["Shopping"])
    }

    @Test fun noPreviousMonthRecordsMeansNoAdaptiveBudgets() {
        val rows = listOf(row(100_000L, "Food", LocalDate.of(2026, 10, 1)))
        assertEquals(emptyMap<String, Long>(),
            HistoricalFinanceAnalysis.adaptiveCategoryBudgets(rows, YearMonth.of(2026, 10)))
    }
}
