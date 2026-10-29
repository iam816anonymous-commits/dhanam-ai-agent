package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.aistudio.dhanam.expense.agent.AgentViewModel

@Composable
fun AgentScreen(viewModel: AgentViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val pending = state.pendingAction
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelPending,
            title = { Text("Review financial change") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nothing has been saved yet. Check the details carefully.")
                    Text(pending.summary, fontWeight = FontWeight.SemiBold)
                    Text("Confirming writes to this device's finance database. Dhanam reads the saved record back before reporting success.")
                }
            },
            confirmButton = { Button(onClick = viewModel::confirmPending, enabled = !state.busy) { Text(if (state.busy) "Saving…" else "Confirm and save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelPending, enabled = !state.busy) { Text("Cancel") } }
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dhanam AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Your private finance companion", style = MaterialTheme.typography.bodyMedium)
                Text("Approved actions only · Local database · Read-back verification", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (message.role == "user") "You" else "Dhanam", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        state.status?.let {
            Text(it, color = if (state.activity == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = input, onValueChange = { input = it.take(1000) }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Tell Dhanam what you need") }, placeholder = { Text("I spent 250 on lunch…") },
            minLines = 1, maxLines = 3, enabled = !state.busy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.submit(input); input = "" }, enabled = input.isNotBlank() && !state.busy, modifier = Modifier.weight(1f)) { Text("Send") }
            OutlinedButton(onClick = { viewModel.submit("help") }, enabled = !state.busy) { Text("Examples") }
        }
        Text("Try “I spent ₹250 on lunch”, “set food budget to 3000”, or “record repayment 500 from Ravi”. Every financial write requires your confirmation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
