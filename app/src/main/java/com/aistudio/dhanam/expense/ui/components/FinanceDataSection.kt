package com.aistudio.dhanam.expense.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.aistudio.dhanam.expense.data.FinanceCsvTransfer
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.FinanceLedgerCsvExporter
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import com.aistudio.dhanam.expense.data.CsvImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FinanceDataSection() {
    val context = LocalContext.current
    val db = remember(context) { FinanceDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val expenses by db.expenseDao().observeAll().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { FinanceCsvTransfer.exportCsv(expenses) }
                withContext(Dispatchers.IO) { FinanceCsvTransfer.writeText(context.contentResolver, uri, text) }
                message = "Exported ${expenses.size} expense record(s)."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Export failed." }
        }
    }
    val ledgerExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val loans = db.financeDao().observeLoans().first()
                    val repayments = db.financeDao().allRepayments()
                    val budgets = db.financeDao().observeBudgetsForMonth(YearMonth.now().atDay(1).toEpochDay()).first()
                    FinanceLedgerCsvExporter.export(loans, repayments, budgets)
                }
                withContext(Dispatchers.IO) { FinanceCsvTransfer.writeText(context.contentResolver, uri, text) }
                message = "Khata, repayment and current-month budget ledger exported."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Ledger export failed." }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                busy = true
                preview = withContext(Dispatchers.IO) {
                    FinanceCsvTransfer.parseCsv(FinanceCsvTransfer.readText(context.contentResolver, uri))
                }
                message = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not read CSV." }
            finally { busy = false }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Backup and reports", style = MaterialTheme.typography.titleMedium)
            Text("Export expenses as CSV or import a CSV backup. Import is previewed and validated before any records are written.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { exportLauncher.launch("dhanam-expenses.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export expenses to CSV")
            }
            Button(onClick = { ledgerExportLauncher.launch("dhanam-khata-ledger.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Khata, repayments and budgets")
            }
            Button(onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Reading CSV…" else "Import expenses from CSV")
            }
            if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
    }

    preview?.let { result ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Review CSV import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${result.expenses.size} valid row(s) ready. Existing exact duplicates will be skipped.")
                    if (result.errors.isNotEmpty()) {
                        Text("${result.errors.size} row error(s):", color = MaterialTheme.colorScheme.error)
                        result.errors.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (result.errors.size > 8) Text("Only the first 8 errors are shown.")
                        Text("Fix the CSV and import again. No rows have been added.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = result.errors.isEmpty() && result.expenses.isNotEmpty(),
                    onClick = {
                        val ready = result
                        preview = null
                        scope.launch {
                            try {
                                val count = withContext(Dispatchers.IO) {
                                    db.financeDao().importExpensesSkippingExactDuplicates(ready.expenses)
                                }
                                message = "Imported ${count} new record(s); exact duplicates were skipped."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "Import failed; no partial import was committed." }
                        }
                    }
                ) { Text("Import valid rows") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } }
        )
    }
}
