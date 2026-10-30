package com.aistudio.dhanam.expense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import com.aistudio.dhanam.expense.model.LocalPromptValidator

@Composable
fun LocalInferenceSection(viewModel: LocalInferenceViewModel, isPreferred: Boolean) {
    val state by viewModel.state.collectAsState()
    var prompt by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Run the model on this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "This is an interactive on-device model chat. The same loaded model is also used by Dhanam's finance agent to interpret flexible expense, income, budget, Khata and repayment requests. This test chat itself does not read or change financial records; financial writes still require your confirmation. Prompts and replies stay in memory and are not uploaded or saved by Dhanam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.eligibility?.let { eligibility ->
                Text(
                    if (eligibility.allowed) {
                        "Runtime resource check passed · RAM available: " + formatRuntimeBytes(eligibility.availableRamBytes) +
                            " · app storage free: " + formatRuntimeBytes(eligibility.freeStorageBytes)
                    } else {
                        "Runtime load is blocked: " + eligibility.reasons.joinToString(" ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (eligibility.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Text(state.status, style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!state.isReady) {
                Button(
                    onClick = viewModel::loadModel,
                    enabled = !state.isLoading && isPreferred && state.eligibility?.allowed == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isLoading) CircularProgressIndicator()
                    else Text("Load verified model")
                }
                if (!isPreferred) Text("First select “Set preferred” above to confirm which model you want to run.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::refreshEligibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Recheck runtime resources")
                }
            } else {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(LocalPromptValidator.MAX_PROMPT_CHARS) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Test prompt") },
                    placeholder = { Text("Ask a general question…") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !state.isGenerating
                )
                Text(prompt.length.toString() + "/" + LocalPromptValidator.MAX_PROMPT_CHARS + " characters", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.sendMessage(prompt); prompt = "" },
                        enabled = !state.isGenerating && prompt.isNotBlank()
                    ) { Text("Generate") }
                    if (state.isGenerating) OutlinedButton(onClick = viewModel::stopGeneration) { Text("Stop") }
                    else OutlinedButton(onClick = viewModel::resetConversation) { Text("New chat") }
                }
                if (state.reply.isNotBlank()) {
                    Text("Local response", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(state.reply, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = viewModel::unloadModel, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isGenerating) "Stop and unload model" else "Unload model")
                }
            }
        }
    }
}

private fun formatRuntimeBytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.0f MiB", bytes / (1024.0 * 1024.0))
