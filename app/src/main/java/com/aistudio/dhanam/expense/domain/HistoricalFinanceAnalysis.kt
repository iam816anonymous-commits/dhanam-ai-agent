package com.aistudio.dhanam.expense.domain

import com.aistudio.dhanam.expense.data.ExpenseEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class YearlySpendTotal(val year: Int, val amountPaise: Long)

data class HistoricalSpendSummary(
    val transactionCount: Int,
    val totalSpentPaise: Long,
    val coveredDays: Long,
    val coveredWeeks: Long,
    val coveredMonths: Long,
    val coveredYears: Long,
    val averagePerDayPaise: Long,
    val averagePerWeekPaise: Long,
    val averagePerMonthPaise: Long,
    val averagePerYearPaise: Long,
    val yearlyTotals: List<YearlySpendTotal>
)

/** All arithmetic is derived from the local ledger; no LLM-generated totals are trusted. */
object HistoricalFinanceAnalysis {
    fun summarize(expenses: List<ExpenseEntity>): HistoricalSpendSummary? {
        val valid = expenses.filter { it.amountPaise > 0 }
        if (valid.isEmpty()) return null
        val first = LocalDate.ofEpochDay(valid.minOf { it.occurredOnEpochDay })
        val last = LocalDate.ofEpochDay(valid.maxOf { it.occurredOnEpochDay })
        val days = (last.toEpochDay() - first.toEpochDay() + 1).coerceAtLeast(1)
        val firstWeek = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeek = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ((lastWeek.toEpochDay() - firstWeek.toEpochDay()) / 7 + 1).coerceAtLeast(1)
        val months = ((YearMonth.from(last).year - YearMonth.from(first).year) * 12L +
            YearMonth.from(last).monthValue - YearMonth.from(first).monthValue + 1).coerceAtLeast(1)
        val years = (last.year - first.year + 1).coerceAtLeast(1)
        val total = valid.fold(0L) { sum, row -> Math.addExact(sum, row.amountPaise) }
        val yearly = valid.groupBy { LocalDate.ofEpochDay(it.occurredOnEpochDay).year }
            .map { (year, rows) ->
                YearlySpendTotal(year, rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountPaise) })
            }.sortedBy { it.year }
        return HistoricalSpendSummary(
            transactionCount = valid.size,
            totalSpentPaise = total,
            coveredDays = days,
            coveredWeeks = weeks,
            coveredMonths = months,
            coveredYears = years.toLong(),
            averagePerDayPaise = total / days,
            averagePerWeekPaise = total / weeks,
            averagePerMonthPaise = total / months,
            averagePerYearPaise = total / years,
            yearlyTotals = yearly
        )
    }

    /**
     * Builds next-month category allocations from the previous complete calendar month.
     * A 10% buffer is added and each category is rounded up to the next ₹100.
     * Empty categories are omitted; the caller must not silently invent a budget for them.
     */
    fun adaptiveCategoryBudgets(
        expenses: List<ExpenseEntity>,
        targetMonth: YearMonth,
        bufferPercent: Int = 10
    ): Map<String, Long> {
        require(bufferPercent in 0..50) { "Budget buffer must be between 0% and 50%." }
        val sourceMonth = targetMonth.minusMonths(1)
        val start = sourceMonth.atDay(1).toEpochDay()
        val end = sourceMonth.plusMonths(1).atDay(1).toEpochDay()
        val totals = expenses.asSequence()
            .filter { it.occurredOnEpochDay >= start && it.occurredOnEpochDay < end && it.amountPaise > 0 }
            .groupBy { it.category }
            .mapValues { (_, rows) -> rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountPaise) } }
        return totals.mapValues { (_, spent) ->
            val buffered = Math.addExact(spent, Math.multiplyExact(spent, bufferPercent.toLong()) / 100L)
            val step = 10_000L
            val roundedNumerator = Math.addExact(buffered, step - 1L)
            val rounded = Math.multiplyExact(roundedNumerator / step, step)
            rounded.coerceAtLeast(step)
        }.toSortedMap()
    }
}
