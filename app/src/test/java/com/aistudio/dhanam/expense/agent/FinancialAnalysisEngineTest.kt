package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.BudgetEntity
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.data.KhataLoanEntity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAnalysisEngineTest {
    private fun expense(title: String, amount: Long, category: String, date: LocalDate) = ExpenseEntity(
        title = title,
        amountPaise = amount,
        category = category,
        occurredOnEpochDay = date.toEpochDay(),
        note = "",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )

    @Test fun comparesMonthToDateAndFindsEvidenceBackedBudgetOverrun() {
        val today = LocalDate.of(2026, 10, 15)
        val rows = listOf(
            expense("Groceries", 100_000L, "Food", today),
            expense("Groceries", 50_000L, "Food", LocalDate.of(2026, 9, 15))
        )
        val budget = BudgetEntity(
            category = "Food",
            monthEpochDay = YearMonth.of(2026, 10).atDay(1).toEpochDay(),
            amountPaise = 80_000L,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val result = FinancialAnalysisEngine.analyze(rows, listOf(budget), emptyList(), today)

        assertEquals(100_000L, result.totalSpentPaise)
        assertEquals(50_000L, result.previousMonthSpentPaise)
        assertEquals(50_000L, result.monthToDateComparablePaise)
        assertEquals(20_000L, result.budgetOverruns["Food"])
        assertTrue(result.opportunities.any { it.id.startsWith("increase-food") })
        assertTrue(result.opportunities.any { it.id.startsWith("budget-overrun-food") })
    }

    @Test fun recurringPaymentsAreOnlyFlaggedAsCandidatesAfterThreeDistinctDates() {
        val today = LocalDate.of(2026, 10, 20)
        val rows = listOf(
            expense("Music plan", 29900L, "Other", today.minusDays(60)),
            expense("Music plan", 29900L, "Other", today.minusDays(30)),
            expense("Music plan", 29900L, "Other", today)
        )
        val result = FinancialAnalysisEngine.analyze(rows, emptyList(), emptyList(), today)
        val candidate = result.opportunities.single()
        assertEquals(InsightConfidence.LOW, candidate.confidence)
        assertTrue(candidate.title.contains("Music plan"))
        assertEquals(null, candidate.estimatedMonthlySavingsPaise)
    }

    @Test fun calculatesLentAndBorrowedOutstandingSeparately() {
        val today = LocalDate.of(2026, 10, 20)
        val base = KhataLoanEntity(
            personName = "A",
            direction = "LENT",
            principalPaise = 10_000L,
            paidPaise = 2_000L,
            openedOnEpochDay = today.minusDays(10).toEpochDay(),
            dueOnEpochDay = null,
            note = "",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val borrowed = base.copy(id = 2L, direction = "BORROWED", principalPaise = 20_000L, paidPaise = 5_000L)
        val result = FinancialAnalysisEngine.analyze(emptyList(), emptyList(), listOf(base, borrowed), today)
        assertEquals(8_000L, result.lentOutstandingPaise)
        assertEquals(15_000L, result.borrowedOutstandingPaise)
    }
}
