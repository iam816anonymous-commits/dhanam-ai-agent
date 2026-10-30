package com.aistudio.dhanam.expense.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.RecommendationFeedbackEntity
import com.aistudio.dhanam.expense.data.FinancePdfReportExporter
import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import com.aistudio.dhanam.expense.domain.FinanceCalculations
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.agent.FinancialAnalysisEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val db = remember(context) { FinanceDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val expenses by db.expenseDao().observeAll().collectAsState(initial = emptyList())
    val incomes by db.financeDao().observeIncome().collectAsState(initial = emptyList())
    val recommendationFeedback by db.financeDao().observeRecommendationFeedback().collectAsState(initial = emptyList())
    val budgets by db.financeDao().observeBudgetsForMonth(month.atDay(1).toEpochDay()).collectAsState(initial = emptyList())
    val loans by db.financeDao().observeLoans().collectAsState(initial = emptyList())
    val intelligenceBudgets by db.financeDao().observeBudgetsForMonth(YearMonth.now().atDay(1).toEpochDay()).collectAsState(initial = emptyList())
    val financialSnapshot = remember(expenses, intelligenceBudgets, loans) {
        FinancialAnalysisEngine.analyze(expenses, intelligenceBudgets, loans, LocalDate.now())
    }
    val summary = remember(expenses, budgets, month) {
        FinanceCalculations.monthlySummary(expenses, budgets, month, LocalDate.now())
    }
    val incomeTotalPaise = remember(incomes, month) {
        val start = month.atDay(1).toEpochDay()
        val end = month.atEndOfMonth().toEpochDay()
        incomes.asSequence()
            .filter { it.occurredOnEpochDay in start..end }
            .fold(0L) { total, record -> Math.addExact(total, record.amountPaise) }
    }
    val hasIncomeRecords = incomes.any {
        it.occurredOnEpochDay in month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()
    }
    val netCashFlowPaise = if (hasIncomeRecords) Math.subtractExact(incomeTotalPaise, summary.totalSpentPaise) else null
    var budgetAmount by remember(month, budgets) {
        mutableStateOf(budgets.firstOrNull { it.category == FinanceCalculations.OVERALL_BUDGET_CATEGORY }
            ?.let { "%d.%02d".format(it.amountPaise / 100, it.amountPaise % 100) } ?: "")
    }
    var selectedBudgetCategory by remember { mutableStateOf(ExpenseInputValidator.categories.first()) }
    var categoryBudgetAmount by remember(month, budgets, selectedBudgetCategory) {
        mutableStateOf(budgets.firstOrNull { it.category == selectedBudgetCategory }
            ?.let { "%d.%02d".format(it.amountPaise / 100, it.amountPaise % 100) } ?: "")
    }
    var message by remember { mutableStateOf<String?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val snapshot = summary
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { FinancePdfReportExporter.write(context.contentResolver, uri, snapshot) }
                    message = "PDF report saved."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "PDF export failed." }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Monthly dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1); message = null }) { Text("‹ Previous") }
            Text(month.toString(), modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { month = month.plusMonths(1); message = null }) { Text("Next ›") }
        }
        SummaryCard("Spent this month", MoneyParser.formatPaise(summary.totalSpentPaise),
            "${summary.transactionCount} recorded transaction(s)")
        SummaryCard("Income recorded", if (hasIncomeRecords) MoneyParser.formatPaise(incomeTotalPaise) else "No income records",
            "${incomes.count { it.occurredOnEpochDay in month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay() }} income record(s) · separate from expenses")
        SummaryCard("Net cash flow", netCashFlowPaise?.let(MoneyParser::formatPaise) ?: "Not available",
            if (hasIncomeRecords) "Recorded income minus recorded expenses for ${month}. This is not a bank balance."
            else "Income has not been recorded for this month; net cash flow cannot be calculated.")
        SummaryCard("Monthly budget", summary.overallBudgetPaise?.let(MoneyParser::formatPaise) ?: "Not set",
            "Set a budget below to calculate remaining funds.")
        SummaryCard("Budget remaining", summary.remainingBudgetPaise?.let {
            (if (it < 0) "Over budget by " else "") + MoneyParser.formatPaise(kotlin.math.abs(it))
        } ?: "Not available", "Based only on expenses saved on this device.")
        SummaryCard("Safe to spend today", summary.safeToSpendTodayPaise?.let(MoneyParser::formatPaise) ?: "Not available",
            if (month == YearMonth.now()) "Remaining budget divided across today and the remaining days of this month."
            else "Available only for the current month with a positive overall budget.")

        SummaryCard("Money owed to you", MoneyParser.formatPaise(financialSnapshot.lentOutstandingPaise),
            "Outstanding principal in your recorded Khata lending entries.")
        SummaryCard("Money you owe", MoneyParser.formatPaise(financialSnapshot.borrowedOutstandingPaise),
            "Outstanding principal in your recorded Khata borrowing entries.")

        Text("Expense intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("These checks use recorded transactions only. They are signals to review, not automatic cuts or guaranteed savings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (financialSnapshot.opportunities.isEmpty()) {
            Text("Not enough evidence for a savings recommendation yet. Keep recording expenses and revisit after more history is available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            financialSnapshot.opportunities.forEach { opportunity ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(opportunity.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(opportunity.evidence, style = MaterialTheme.typography.bodySmall)
                        Text(opportunity.suggestedAction, style = MaterialTheme.typography.bodyMedium)
                        Text("Confidence: ${opportunity.confidence.name.lowercase()} · Estimated monthly saving: " +
                            (opportunity.estimatedMonthlySavingsPaise?.let(MoneyParser::formatPaise) ?: "Not estimated"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        val feedback = recommendationFeedback.firstOrNull {
                            it.recommendationId == opportunity.id && it.periodMonth == month.toString()
                        }
                        Text(
                            feedback?.let { "Your feedback: ${it.decision.replace("_", " ").lowercase()}" }
                                ?: "Was this recommendation useful?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    db.financeDao().upsertRecommendationFeedback(
                                        RecommendationFeedbackEntity(
                                            recommendationId = opportunity.id,
                                            periodMonth = month.toString(),
                                            decision = "HELPFUL",
                                            createdAtEpochMillis = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }) { Text(if (feedback?.decision == "HELPFUL") "✓ Helpful" else "Helpful") }
                            TextButton(onClick = {
                                scope.launch {
                                    db.financeDao().upsertRecommendationFeedback(
                                        RecommendationFeedbackEntity(
                                            recommendationId = opportunity.id,
                                            periodMonth = month.toString(),
                                            decision = "NOT_USEFUL",
                                            createdAtEpochMillis = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }) { Text(if (feedback?.decision == "NOT_USEFUL") "✓ Not useful" else "Not useful") }
                        }
                    }
                }
            }
        }
        Text("Category spending movement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Compares this month's recorded spending so far with the same number of days in the previous month. It is not a full-month comparison.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (financialSnapshot.categoryMovements.isEmpty()) {
            Text("Category comparisons will appear after expenses are recorded in this month or the previous month.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            financialSnapshot.categoryMovements.take(6).forEach { movement ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(movement.category, fontWeight = FontWeight.SemiBold)
                        Text("This month so far: ${MoneyParser.formatPaise(movement.currentMonthPaise)}")
                        Text("Previous month, same period: ${MoneyParser.formatPaise(movement.previousMonthPaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val movementLabel = when {
                            movement.changePaise > 0 -> "Increased by "
                            movement.changePaise < 0 -> "Decreased by "
                            else -> "No change: "
                        } + MoneyParser.formatPaise(kotlin.math.abs(movement.changePaise)) +
                            (movement.changePercent?.let { " (${if (it > 0) "+" else ""}$it%)" } ?: "")
                        Text(movementLabel,
                            color = if (movement.changePaise > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Text("Data coverage: ${financialSnapshot.dataCoverageDays} day(s) since the earliest recorded expense. Sparse history can make comparisons unreliable.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Overall monthly budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = budgetAmount, onValueChange = { budgetAmount = it },
                    label = { Text("Budget amount (₹)") }, placeholder = { Text("e.g. 15000.00") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    try {
                        val amount = MoneyParser.parseRupeesToPaise(budgetAmount)
                        scope.launch {
                            try {
                                db.financeDao().saveBudget(FinanceCalculations.OVERALL_BUDGET_CATEGORY,
                                    month.atDay(1).toEpochDay(), amount, System.currentTimeMillis())
                                message = "Overall budget saved on this device."
                            } catch (e: Exception) { message = e.message ?: "Could not save budget." }
                        }
                    } catch (e: Exception) { message = e.message ?: "Enter a valid budget." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save overall budget") }

                Text("Category budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpenseInputValidator.categories.chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { category ->
                                FilterChip(selected = selectedBudgetCategory == category,
                                    onClick = { selectedBudgetCategory = category },
                                    label = { Text(category) })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = categoryBudgetAmount, onValueChange = { categoryBudgetAmount = it },
                    label = { Text("Monthly limit for $selectedBudgetCategory (₹)") },
                    placeholder = { Text("e.g. 2500.00") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    try {
                        val amount = MoneyParser.parseRupeesToPaise(categoryBudgetAmount)
                        scope.launch {
                            try {
                                db.financeDao().saveBudget(selectedBudgetCategory, month.atDay(1).toEpochDay(),
                                    amount, System.currentTimeMillis())
                                message = "$selectedBudgetCategory budget saved."
                            } catch (e: Exception) { message = e.message ?: "Could not save category budget." }
                        }
                    } catch (e: Exception) { message = e.message ?: "Enter a valid category budget." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save category budget") }
                if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
                budgets.firstOrNull { it.category == FinanceCalculations.OVERALL_BUDGET_CATEGORY }?.let { overallBudget ->
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                db.financeDao().deleteBudgetById(overallBudget.id)
                                message = "Overall budget removed for $month."
                            } catch (e: Exception) { message = e.message ?: "Could not remove overall budget." }
                        }
                    }) { Text("Remove overall budget") }
                }
                Button(onClick = { pdfLauncher.launch("dhanam-report-$month.pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export monthly report as PDF")
                }
            }
        }

        Text("Category spending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (summary.categoryTotalsPaise.isEmpty()) {
            Text("No expenses recorded in this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            summary.categoryTotalsPaise.forEach { (category, total) ->
                SummaryCard(category, MoneyParser.formatPaise(total),
                    if (summary.totalSpentPaise > 0) "${total * 100 / summary.totalSpentPaise}% of recorded spending" else "No spending")
            }
        }

        Text("Budget status by category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val categoryBudgets = budgets.filter { it.category != FinanceCalculations.OVERALL_BUDGET_CATEGORY }
        if (categoryBudgets.isEmpty()) {
            Text("No category budgets set for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            categoryBudgets.forEach { budget ->
                val spent = summary.categoryTotalsPaise[budget.category] ?: 0L
                val remaining = budget.amountPaise - spent
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(budget.category, fontWeight = FontWeight.SemiBold)
                        Text("Limit: ${MoneyParser.formatPaise(budget.amountPaise)} · Spent: ${MoneyParser.formatPaise(spent)}")
                        Text(if (remaining < 0) "Over limit by ${MoneyParser.formatPaise(kotlin.math.abs(remaining))}"
                            else "Remaining: ${MoneyParser.formatPaise(remaining)}",
                            color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    db.financeDao().deleteBudgetById(budget.id)
                                    message = "${budget.category} budget removed."
                                } catch (e: Exception) { message = e.message ?: "Could not remove budget." }
                            }
                        }) { Text("Remove budget") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
