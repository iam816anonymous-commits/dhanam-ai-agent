package com.aistudio.dhanam.expense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.dhanam.expense.model.ModelCatalog
import com.aistudio.dhanam.expense.model.ModelManagerViewModel
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import java.util.Locale

@Composable
fun ModelManagerSection(viewModel: ModelManagerViewModel = viewModel(), inferenceViewModel: LocalInferenceViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val model = ModelCatalog.models.first()
    val eligibility = state.eligibility
    val inferenceState by inferenceViewModel.state.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local AI model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Models stay in this app's private storage. Nothing downloads until you tap Download. No cloud inference or fallback is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("474.61 MiB · Apache-2.0 · 2,048-token context", style = MaterialTheme.typography.bodySmall)
                Text("Pinned source revision; downloads are checked against exact byte size and SHA-256.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Resource gate: at least 3,500 MiB total RAM, 2,304 MiB currently available RAM, and 1,280 MiB free app storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (eligibility == null) {
                    Text("Checking device resources…", style = MaterialTheme.typography.bodyMedium)
                } else if (eligibility.allowed) {
                    Text("Device currently meets the download resource checks.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    eligibility.reasons.forEach { reason ->
                        Text("• $reason", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.isDownloading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(state.progress * 100).toInt()}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.isVerified) {
                    Text("Verified model file is installed.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    if (state.preferredModelId == model.id) {
                        Text("Saved as preferred model.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isDownloading) {
                        OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
                    } else if (!state.isVerified) {
                        Button(onClick = viewModel::download, enabled = eligibility?.allowed == true) { Text("Download model") }
                        OutlinedButton(onClick = viewModel::refresh) { Text("Recheck") }
                    } else {
                        Button(onClick = viewModel::setPreferred) { Text("Set preferred") }
                        OutlinedButton(onClick = viewModel::deleteModel, enabled = !inferenceState.isReady && !inferenceState.isLoading && !inferenceState.isGenerating) { Text("Remove") }
                    }
                }
                Text(
                    "LiteRT-LM CPU inference is available below. GPU/NPU acceleration is not enabled until separately qualified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.isVerified) {
            LocalInferenceSection(
                viewModel = inferenceViewModel,
                isPreferred = state.preferredModelId == model.id
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MiB"
    return String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
}
