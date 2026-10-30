package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.BudgetEntity
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.data.KhataLoanEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class InsightConfidence { LOW, MEDIUM, HIGH }

data class CategoryMovement(
    val category: String,
    val currentMonthPaise: Long,
    val previousMonthPaise: Long,
    val changePaise: Long,
    val changePercent: Int?
)

data class SavingsOpportunity(
    val id: String,
    val title: String,
    val evidence: String,
    val suggestedAction: String,
    val estimatedMonthlySavingsPaise: Long?,
    val confidence: InsightConfidence
)

data class FinancialSnapshot(
    val month: YearMonth,
    val totalSpentPaise: Long,
    val previousMonthSpentPaise: Long,
    val monthToDateComparablePaise: Long,
    val categoryMovements: List<CategoryMovement>,
    val budgetOverruns: Map<String, Long>,
    val lentOutstandingPaise: Long,
    val borrowedOutstandingPaise: Long,
    val opportunities: List<SavingsOpportunity>,
    val dataCoverageDays: Long
)

/** Deterministic local-ledger analysis. The model never supplies arithmetic or evidence. */
object FinancialAnalysisEngine {
    fun analyze(
        expenses: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        loans: List<KhataLoanEntity>,
        today: LocalDate
    ): FinancialSnapshot {
        val month = YearMonth.from(today)
        val currentStart = month.atDay(1).toEpochDay()
        val previous = month.minusMonths(1)
        val previousStart = previous.atDay(1).toEpochDay()
        val previousEnd = previous.atEndOfMonth().toEpochDay()
        val comparableDay = minOf(today.dayOfMonth, previous.lengthOfMonth())
        val comparablePreviousEnd = previous.atDay(comparableDay).toEpochDay()
        val currentExpenses = expenses.filter { it.occurredOnEpochDay in currentStart..today.toEpochDay() && it.amountPaise > 0 }
        val previousExpenses = expenses.filter { it.occurredOnEpochDay in previousStart..previousEnd && it.amountPaise > 0 }
        val comparablePrevious = expenses.filter { it.occurredOnEpochDay in previousStart..comparablePreviousEnd && it.amountPaise > 0 }
        val currentTotal = safeSum(currentExpenses.map { it.amountPaise })
        val previousTotal = safeSum(previousExpenses.map { it.amountPaise })
        val comparableTotal = safeSum(comparablePrevious.map { it.amountPaise })
        val currentByCategory = currentExpenses.groupBy { it.category }.mapValues { safeSum(it.value.map(ExpenseEntity::amountPaise)) }
        // Compare equal month-to-date windows; comparing a partial current month to a full prior month is misleading.
        val previousByCategory = comparablePrevious.groupBy { it.category }.mapValues { safeSum(it.value.map(ExpenseEntity::amountPaise)) }
        val movements = (currentByCategory.keys + previousByCategory.keys).distinct().map { category ->
            val current = currentByCategory[category] ?: 0L
            val prior = previousByCategory[category] ?: 0L
            val change = current - prior
            CategoryMovement(category, current, prior, change,
                if (prior > 0) ((change.toDouble() / prior.toDouble()) * 100.0).toInt() else null)
        }.sortedByDescending { it.currentMonthPaise }

        val budgetByCategory = budgets.filter { it.monthEpochDay == currentStart && it.amountPaise > 0 }
            .associateBy { it.category }
        val overruns = budgetByCategory.mapNotNull { (category, budget) ->
            val spent = currentByCategory[category] ?: 0L
            if (spent > budget.amountPaise) category to (spent - budget.amountPaise) else null
        }.toMap()

        val opportunities = mutableListOf<SavingsOpportunity>()
        movements.filter { it.changePaise >= 50_000L && (it.changePercent ?: 0) >= 20 }
            .take(3).forEach { movement ->
                opportunities += SavingsOpportunity(
                    id = "increase-${movement.category.lowercase(Locale.ROOT)}-${month}",
                    title = "Review the increase in ${movement.category}",
                    evidence = "Recorded ${movement.currentMonthPaise} paise in the first ${today.dayOfMonth} day(s) of this month versus ${movement.previousMonthPaise} paise in the same number of days last month; increase ${movement.changePercent ?: 0}%.",
                    suggestedAction = "Review the largest transactions in this category and test a small, reversible reduction. Do not cut essential spending without checking the underlying need.",
                    // An increase alone does not establish a safe reduction target or a savings amount.
                    estimatedMonthlySavingsPaise = null,
                    confidence = InsightConfidence.MEDIUM
                )
            }
        overruns.forEach { (category, overrun) ->
            opportunities += SavingsOpportunity(
                id = "budget-overrun-${category.lowercase(Locale.ROOT)}-${month}",
                title = "${category} spending is over budget",
                evidence = "Recorded spending exceeds the saved category budget by ${overrun} paise.",
                suggestedAction = "Review recent transactions and adjust future discretionary purchases or the budget if the original limit is no longer realistic.",
                estimatedMonthlySavingsPaise = null,
                confidence = InsightConfidence.HIGH
            )
        }

        val recentStart = today.minusDays(89).toEpochDay()
        val recurring = expenses.asSequence()
            .filter { it.amountPaise > 0 && it.occurredOnEpochDay in recentStart..today.toEpochDay() }
            .groupBy { it.title.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ") }
            .filter { (title, rows) -> title.length >= 3 && rows.map { it.occurredOnEpochDay }.distinct().size >= 3 }
        recurring.entries.take(3).forEach { (_, rows) ->
            val average = safeSum(rows.map { it.amountPaise }) / rows.size
            opportunities += SavingsOpportunity(
                id = "recurring-${rows.first().title.hashCode()}",
                title = "Check repeated payment: ${rows.first().title}",
                evidence = "The same expense description appears on ${rows.map { it.occurredOnEpochDay }.distinct().size} separate dates in the last 90 days; average recorded amount ${average} paise.",
                suggestedAction = "Confirm whether this is a necessary recurring payment. If optional, compare cancellation or a lower-cost plan before changing anything.",
                estimatedMonthlySavingsPaise = null,
                confidence = InsightConfidence.LOW
            )
        }

        val coverageStart = expenses.minOfOrNull { it.occurredOnEpochDay } ?: today.toEpochDay()
        val coverage = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(coverageStart), today).coerceAtLeast(0)
        return FinancialSnapshot(
            month, currentTotal, previousTotal, comparableTotal, movements, overruns,
            safeSum(loans.filter { it.direction.equals("LENT", true) }.map { it.outstandingPaise }),
            safeSum(loans.filter { it.direction.equals("BORROWED", true) }.map { it.outstandingPaise }),
            opportunities, coverage
        )
    }

    private fun safeSum(values: List<Long>): Long = values.fold(0L) { total, value -> Math.addExact(total, value) }
}
