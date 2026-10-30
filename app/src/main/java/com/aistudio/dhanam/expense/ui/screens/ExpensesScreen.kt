package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.ui.ExpensesViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel = viewModel()) {
    val expenses by viewModel.expenses.collectAsState()
    val message by viewModel.message.collectAsState()
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseInputValidator.categories.first()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var dateFilter by remember { mutableStateOf("This month") }
    val visibleExpenses = expenses.filter { expense ->
        val query = searchQuery.trim()
        (selectedCategory == "All" || expense.category == selectedCategory) &&
            (dateFilter == "All dates" ||
                YearMonth.from(LocalDate.ofEpochDay(expense.occurredOnEpochDay)).toString() ==
                    (if (dateFilter == "This month") YearMonth.now().toString() else YearMonth.now().minusMonths(1).toString())) &&
            (query.isEmpty() || expense.title.contains(query, ignoreCase = true) ||
                expense.note.contains(query, ignoreCase = true) || expense.category.contains(query, ignoreCase = true))
    }

    fun resetForm() {
        title = ""
        amount = ""
        category = ExpenseInputValidator.categories.first()
        date = LocalDate.now().toString()
        note = ""
        editingId = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Expenses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Saved only on this device. Amounts are stored as integer paise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (editingId == null) "Add an expense" else "Edit expense",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Description") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)") },
                    placeholder = { Text("125.50") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpenseInputValidator.categories.chunked(4).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { item ->
                                FilterChip(
                                    selected = category == item, onClick = { category = item },
                                    label = { Text(item) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.saveExpense(editingId, title, amount, category, date, note) },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (editingId == null) "Save expense" else "Update expense") }
                    if (editingId != null) TextButton(onClick = { resetForm() }) { Text("Cancel") }
                }
                if (message != null) {
                    Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { viewModel.clearMessage() }) { Text("Dismiss") }
                }
            }
        }

        Text("Search and filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            label = { Text("Search description, note or category") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (listOf("All") + ExpenseInputValidator.categories).chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEach { filter ->
                        FilterChip(selected = selectedCategory == filter, onClick = { selectedCategory = filter }, label = { Text(filter) })
                    }
                }
            }
        }
        Text("Date range", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("This month", "Previous month", "All dates").forEach { filter ->
                FilterChip(selected = dateFilter == filter, onClick = { dateFilter = filter }, label = { Text(filter) })
            }
        }
        Text("Transaction history (${visibleExpenses.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (visibleExpenses.isEmpty()) {
            Text(
                if (expenses.isEmpty()) "No expenses yet. Add your first real transaction above." else "No records match the current search or category filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleExpenses.forEach { expense ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(expense.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${expense.category} · ${viewModel.dateFor(expense)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (expense.note.isNotBlank()) Text(expense.note, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    MoneyParser.formatPaise(expense.amountPaise),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                editingId = expense.id
                                title = expense.title
                                amount = "%d.%02d".format(expense.amountPaise / 100, expense.amountPaise % 100)
                                category = expense.category
                                date = viewModel.dateFor(expense)
                                note = expense.note
                                viewModel.clearMessage()
                            }) { Icon(Icons.Default.Edit, contentDescription = "Edit ${expense.title}") }
                            IconButton(onClick = { pendingDelete = expense }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${expense.title}")
                            }
                        }
                    }
                }
            }
        }

        IncomeSection()
    }

    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete expense?") },
            text = { Text("Delete “${expense.title}” (${MoneyParser.formatPaise(expense.amountPaise)})? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    if (editingId == expense.id) resetForm()
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}
