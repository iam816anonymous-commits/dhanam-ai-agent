package com.aistudio.dhanam.expense.domain

import com.aistudio.dhanam.expense.data.BudgetEntity
import com.aistudio.dhanam.expense.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth

data class MonthlyFinanceSummary(
    val month: YearMonth,
    val totalSpentPaise: Long,
    val transactionCount: Int,
    val categoryTotalsPaise: Map<String, Long>,
    val overallBudgetPaise: Long?,
    val remainingBudgetPaise: Long?,
    val safeToSpendTodayPaise: Long?
)

object FinanceCalculations {
    fun monthlySummary(
        expenses: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        month: YearMonth,
        today: LocalDate
    ): MonthlyFinanceSummary {
        val start = month.atDay(1).toEpochDay()
        val endExclusive = month.plusMonths(1).atDay(1).toEpochDay()
        val monthExpenses = expenses.filter { it.occurredOnEpochDay >= start && it.occurredOnEpochDay < endExclusive }
        val categoryTotals = monthExpenses.groupBy { it.category }.mapValues { (_, records) ->
            records.fold(0L) { total, expense -> Math.addExact(total, expense.amountPaise) }
        }.toSortedMap()
        val spent = monthExpenses.fold(0L) { total, expense -> Math.addExact(total, expense.amountPaise) }
        val overall = budgets.firstOrNull { it.category == OVERALL_BUDGET_CATEGORY && it.monthEpochDay == start }?.amountPaise
        val remaining = overall?.let { it - spent }
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        val safe = remaining?.takeIf { it > 0 }?.let { it / daysLeft.coerceAtLeast(1) }
        return MonthlyFinanceSummary(month, spent, monthExpenses.size, categoryTotals, overall, remaining, safe)
    }

    const val OVERALL_BUDGET_CATEGORY = "Overall"
}
