package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.dhanam.expense.data.IncomeEntity
import com.aistudio.dhanam.expense.domain.IncomeInputValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.ui.IncomeViewModel
import java.time.LocalDate

@Composable
fun IncomeSection(viewModel: IncomeViewModel = viewModel()) {
    val records by viewModel.income.collectAsState()
    val message by viewModel.message.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(IncomeInputValidator.categories.first()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<IncomeEntity?>(null) }

    fun resetForm() {
        title = ""
        amount = ""
        category = IncomeInputValidator.categories.first()
        date = LocalDate.now().toString()
        note = ""
        editingId = null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Income tracking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${records.size} saved income record(s) · stored separately from expenses",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Manage") }
            }
            if (expanded) {
                Text(if (editingId == null) "Add income" else "Edit income",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(title, { title = it }, label = { Text("Description / source") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") },
                    placeholder = { Text("25000.00") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Income category", style = MaterialTheme.typography.labelLarge)
                IncomeInputValidator.categories.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        group.forEach { item ->
                            FilterChip(selected = category == item, onClick = { category = item },
                                label = { Text(item) })
                        }
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    viewModel.saveIncome(editingId, title, amount, category, date, note)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editingId == null) "Save income" else "Update income")
                }
                if (editingId != null) {
                    TextButton(onClick = { resetForm(); viewModel.clearMessage() }) { Text("Cancel edit") }
                }
                if (message != null) {
                    Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { viewModel.clearMessage() }) { Text("Dismiss") }
                }
                Text("Income history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (records.isEmpty()) {
                    Text("No income records yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    records.take(20).forEach { record ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(record.title, fontWeight = FontWeight.SemiBold)
                                Text("${record.category} · ${viewModel.dateFor(record)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(MoneyParser.formatPaise(record.amountPaise),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium)
                                if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        editingId = record.id
                                        title = record.title
                                        amount = "%d.%02d".format(record.amountPaise / 100, record.amountPaise % 100)
                                        category = record.category
                                        date = viewModel.dateFor(record)
                                        note = record.note
                                    }) { Text("Edit") }
                                    TextButton(onClick = { pendingDelete = record }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    if (records.size > 20) {
                        Text("Showing the latest 20 income records.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete income record?") },
            text = { Text("Delete “${record.title}” (${MoneyParser.formatPaise(record.amountPaise)})?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteIncome(record.id)
                    if (editingId == record.id) resetForm()
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}
