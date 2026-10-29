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
import com.aistudio.dhanam.expense.data.FinancePdfReportExporter
import com.aistudio.dhanam.expense.domain.FinanceCalculations
import com.aistudio.dhanam.expense.domain.MoneyParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val budgets by db.financeDao().observeBudgetsForMonth(month.atDay(1).toEpochDay()).collectAsState(initial = emptyList())
    val summary = remember(expenses, budgets, month) {
        FinanceCalculations.monthlySummary(expenses, budgets, month, LocalDate.now())
    }
    var budgetAmount by remember(month, budgets) {
        mutableStateOf(budgets.firstOrNull { it.category == FinanceCalculations.OVERALL_BUDGET_CATEGORY }
            ?.let { "%d.%02d".format(it.amountPaise / 100, it.amountPaise % 100) } ?: "")
    }
    var message by remember { mutableStateOf<String?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val snapshot = summary
            scope.launch(Dispatchers.IO) {
                try {
                    FinancePdfReportExporter.write(context.contentResolver, uri, snapshot)
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
        SummaryCard("Monthly budget", summary.overallBudgetPaise?.let(MoneyParser::formatPaise) ?: "Not set",
            "Set a budget below to calculate remaining funds.")
        SummaryCard("Budget remaining", summary.remainingBudgetPaise?.let {
            (if (it < 0) "Over budget by " else "") + MoneyParser.formatPaise(kotlin.math.abs(it))
        } ?: "Not available", "Based only on expenses saved on this device.")
        SummaryCard("Safe to spend today", summary.safeToSpendTodayPaise?.let(MoneyParser::formatPaise) ?: "Not available",
            "Remaining budget divided across today and the remaining days of this month.")
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
                                message = "Budget saved on this device."
                            } catch (e: Exception) { message = e.message ?: "Could not save budget." }
                        }
                    } catch (e: Exception) { message = e.message ?: "Enter a valid budget." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
                Button(onClick = { pdfLauncher.launch("dhanam-report-${month}.pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export monthly report as PDF")
                }
                if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
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
