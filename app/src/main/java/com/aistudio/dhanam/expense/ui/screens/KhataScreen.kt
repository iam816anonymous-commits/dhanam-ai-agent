package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.dhanam.expense.data.KhataLoanEntity
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.ui.KhataViewModel
import java.time.LocalDate
import java.util.UUID

@Composable
fun KhataScreen(viewModel: KhataViewModel = viewModel()) {
    val loans by viewModel.loans.collectAsState()
    val message by viewModel.message.collectAsState()
    var editingLoanId by remember { mutableStateOf<Long?>(null) }
    var person by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(KhataValidator.LENT) }
    var amount by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(LocalDate.now().toString()) }
    var due by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var repaymentFor by remember { mutableStateOf<KhataLoanEntity?>(null) }
    var repaymentAmount by remember { mutableStateOf("") }
    var repaymentDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var repaymentNote by remember { mutableStateOf("") }
    var repaymentRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var deleteFor by remember { mutableStateOf<KhataLoanEntity?>(null)}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Lending Khata", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Track money lent to others or borrowed from them. Records stay on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (editingLoanId == null) "New Khata record" else "Edit Khata record", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(person, { person = it }, label = { Text("Person's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(direction == KhataValidator.LENT, { direction = KhataValidator.LENT }, enabled = editingLoanId == null, label = { Text("I lent money") })
                    FilterChip(direction == KhataValidator.BORROWED, { direction = KhataValidator.BORROWED }, enabled = editingLoanId == null, label = { Text("I borrowed") })
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Principal amount (₹)") }, placeholder = { Text("1000.00") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(opened, { opened = it }, label = { Text("Start date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(due, { due = it }, label = { Text("Due date (optional, YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val id = editingLoanId
                    if (id == null) viewModel.addLoan(person, direction, amount, opened, due, note, phoneNumber)
                    else viewModel.updateLoan(id, person, direction, amount, opened, due, note, phoneNumber)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (editingLoanId == null) "Save Khata record" else "Update Khata record")
                }
                if (editingLoanId != null) {
                    TextButton(onClick = {
                        editingLoanId = null
                        person = ""
                        direction = KhataValidator.LENT
                        amount = ""
                        opened = LocalDate.now().toString()
                        due = ""
                        note = ""
                        phoneNumber = ""
                        viewModel.clearMessage()
                    }) { Text("Cancel edit") }
                }
            }
        }
        if (message != null) {
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { viewModel.clearMessage() }) { Text("Dismiss") }
        }
        Text("Your records (${loans.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (loans.isEmpty()) Text("No Khata records yet. Add a real record above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        loans.forEach { loan ->
            key(loan.id) {
                KhataLoanCard(
                    loan = loan,
                    viewModel = viewModel,
                    onEdit = {
                        editingLoanId = loan.id
                        person = loan.personName
                        direction = loan.direction
                        amount = "%d.%02d".format(loan.principalPaise / 100, loan.principalPaise % 100)
                        opened = LocalDate.ofEpochDay(loan.openedOnEpochDay).toString()
                        due = loan.dueOnEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()
                        note = loan.note
                        phoneNumber = loan.phoneNumber
                        viewModel.clearMessage()
                    },
                    onRepay = {
                        repaymentFor = loan
                        repaymentAmount = ""
                        repaymentDate = LocalDate.now().toString()
                        repaymentNote = ""
                    },
                    onDelete = { deleteFor = loan }
                )
            }
        }
    }

    repaymentFor?.let { loan ->
        AlertDialog(
            onDismissRequest = { repaymentFor = null },
            title = { Text("Record repayment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${loan.personName} · outstanding ${MoneyParser.formatPaise(loan.outstandingPaise)}")
                    OutlinedTextField(repaymentAmount, { repaymentAmount = it }, label = { Text("Amount (₹)") }, singleLine = true)
                    OutlinedTextField(repaymentDate, { repaymentDate = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(repaymentNote, { repaymentNote = it }, label = { Text("Note (optional)") })
                }
            },
            confirmButton = { TextButton(onClick = {
                viewModel.recordRepayment(loan.id, repaymentAmount, repaymentDate, repaymentNote, repaymentRequestId)
                repaymentFor = null
            }) { Text("Save repayment") } },
            dismissButton = { TextButton(onClick = { repaymentFor = null }) { Text("Cancel") } }
        )
    }
    deleteFor?.let { loan ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete Khata record?") },
            text = { Text("Delete the record for ${loan.personName}? Records with repayment history cannot be deleted.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLoan(loan.id); deleteFor = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun KhataLoanCard(
    loan: KhataLoanEntity,
    viewModel: KhataViewModel,
    onEdit: () -> Unit,
    onRepay: () -> Unit,
    onDelete: () -> Unit
) {
    val repayments by remember(loan.id) { viewModel.repaymentsFor(loan.id) }.collectAsState(initial = emptyList())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(loan.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (loan.direction == KhataValidator.LENT) "You lent" else "You borrowed",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Principal: ${MoneyParser.formatPaise(loan.principalPaise)}")
            Text("Paid: ${MoneyParser.formatPaise(loan.paidPaise)}")
            Text("Outstanding: ${MoneyParser.formatPaise(loan.outstandingPaise)}",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Started ${LocalDate.ofEpochDay(loan.openedOnEpochDay)}" +
                (loan.dueOnEpochDay?.let { " · Due ${LocalDate.ofEpochDay(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            if (loan.phoneNumber.isNotBlank()) Text("Phone: ${loan.phoneNumber}", style = MaterialTheme.typography.bodySmall)
            if (loan.note.isNotBlank()) Text(loan.note, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                Button(onClick = onRepay, enabled = loan.outstandingPaise > 0) { Text("Record repayment") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            Text("Repayment history (${repayments.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (repayments.isEmpty()) Text("No repayments recorded yet.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            repayments.take(10).forEach { repayment ->
                Text("${LocalDate.ofEpochDay(repayment.paidOnEpochDay)} · ${MoneyParser.formatPaise(repayment.amountPaise)}" +
                    (repayment.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (repayments.size > 10) Text("Showing the latest 10 repayments.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
