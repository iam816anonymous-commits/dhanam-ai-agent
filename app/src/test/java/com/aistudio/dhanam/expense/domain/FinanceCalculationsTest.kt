package com.aistudio.dhanam.expense.domain

import com.aistudio.dhanam.expense.data.BudgetEntity
import com.aistudio.dhanam.expense.data.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceCalculationsTest {
    private fun expense(date: LocalDate, amount: Long, category: String = "Food") = ExpenseEntity(
        title = "test", amountPaise = amount, category = category, occurredOnEpochDay = date.toEpochDay(),
        note = "", createdAtEpochMillis = 1, updatedAtEpochMillis = 1
    )

    @Test fun monthlyTotalsExcludeAdjacentMonthsAndUseExactPaise() {
        val month = YearMonth.of(2026, 10)
        val records = listOf(
            expense(LocalDate.of(2026, 10, 1), 10001),
            expense(LocalDate.of(2026, 10, 31), 999),
            expense(LocalDate.of(2026, 11, 1), 50000),
            expense(LocalDate.of(2026, 9, 30), 30000)
        )
        val result = FinanceCalculations.monthlySummary(records, emptyList(), month, LocalDate.of(2026, 10, 29))
        assertEquals(11000L, result.totalSpentPaise)
        assertEquals(2, result.transactionCount)
        assertEquals(11000L, result.categoryTotalsPaise["Food"])
    }

    @Test fun noBudgetMeansNoInventedSafeToSpend() {
        val result = FinanceCalculations.monthlySummary(emptyList(), emptyList(), YearMonth.of(2026, 10), LocalDate.of(2026, 10, 29))
        assertNull(result.overallBudgetPaise)
        assertNull(result.safeToSpendTodayPaise)
    }

    @Test fun overspendingProducesNegativeRemainingAndNoPositiveDailyAllowance() {
        val month = YearMonth.of(2026, 10)
        val budget = BudgetEntity(category = "Overall", monthEpochDay = month.atDay(1).toEpochDay(),
            amountPaise = 10000, createdAtEpochMillis = 1, updatedAtEpochMillis = 1)
        val result = FinanceCalculations.monthlySummary(
            listOf(expense(LocalDate.of(2026, 10, 3), 15000)), listOf(budget), month, LocalDate.of(2026, 10, 29)
        )
        assertEquals(-5000L, result.remainingBudgetPaise)
        assertNull(result.safeToSpendTodayPaise)
    }
}
