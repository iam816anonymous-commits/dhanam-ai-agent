package com.aistudio.dhanam.expense.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.LegacyFinanceMigration
import com.aistudio.dhanam.expense.data.LegacyFinancePreview
import com.aistudio.dhanam.expense.domain.MoneyParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun LegacyMigrationSection() {
    val context = LocalContext.current
    val db = remember(context) { FinanceDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("dhanam_migration", 0) }
    var preview by remember { mutableStateOf<LegacyFinancePreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var alreadyImported by remember { mutableStateOf(prefs.getBoolean("legacy_finance_imported", false)) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Legacy Dhanam data", style = MaterialTheme.typography.titleMedium)
            Text(
                "If this app was installed over the previous Dhanam version, review its existing database and import expenses, Khata loans and category budgets. The old database is opened read-only and never deleted.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            preview = withContext(Dispatchers.IO) { LegacyFinanceMigration.inspect(context) }
                            message = null
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "Could not inspect legacy data." }
                        finally { busy = false }
                    }
                },
                enabled = !busy && !alreadyImported,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (alreadyImported) "Legacy import already completed" else if (busy) "Inspecting…" else "Review legacy data migration") }
            if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
            if (alreadyImported) Text("A legacy import was previously confirmed on this installation. Exact duplicates are also skipped during import.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Review legacy data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Source: previous Dhanam database")
                    Text("Expenses: ${data.expenses.size}")
                    Text("Khata loans: ${data.loans.size}")
                    Text("Budgets: ${data.budgets.size} (legacy overall budget preference may be the old default; category budgets are mapped to the current month)")
                    data.budgets.take(8).forEach { Text("${it.category}: ${MoneyParser.formatPaise(it.amountPaise)}", style = MaterialTheme.typography.bodySmall) }
                    if (data.budgets.size > 8) Text("Showing first 8 budgets.")
                    Text("Legacy Double amounts are converted to paise using decimal HALF_UP rounding.")
                    if (data.errors.isNotEmpty()) {
                        Text("${data.errors.size} invalid row(s) found. No data can be imported until these rows are resolved.",
                            color = MaterialTheme.colorScheme.error)
                        data.errors.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (data.errors.size > 6) Text("Only the first 6 errors are shown.")
                    }
                    if (data.isEmpty) Text("No records were found in the legacy database.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = data.errors.isEmpty() && !data.isEmpty,
                    onClick = {
                        preview = null
                        scope.launch {
                            try {
                                val counts = withContext(Dispatchers.IO) {
                                    db.financeDao().importLegacyRecords(data.expenses, data.loans, data.budgets)
                                }
                                prefs.edit().putBoolean("legacy_finance_imported", true).apply()
                                alreadyImported = true
                                message = "Imported ${counts.expenses} expenses, ${counts.loans} loans and ${counts.budgets} budgets. Previously imported source rows were skipped; different source IDs were preserved, and the old database was kept."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "Legacy import failed; transaction rolled back." }
                        }
                    }
                ) { Text("Import reviewed data") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } }
        )
    }
}
