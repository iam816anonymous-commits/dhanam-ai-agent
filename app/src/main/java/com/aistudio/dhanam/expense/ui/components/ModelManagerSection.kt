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
import com.aistudio.dhanam.expense.model.ModelTier
import com.aistudio.dhanam.expense.model.ModelManagerViewModel
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import java.util.Locale

@Composable
fun ModelManagerSection(viewModel: ModelManagerViewModel = viewModel(), inferenceViewModel: LocalInferenceViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val inferenceState by inferenceViewModel.state.collectAsState()
    val model = ModelCatalog.models.first { it.id == state.selectedModelId }
    val canSwitchModel = !state.isDownloading && !inferenceState.isReady && !inferenceState.isLoading && !inferenceState.isGenerating
    val eligibility = state.eligibility

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local AI model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Models stay in this app's private storage. Nothing downloads until you tap Download. No cloud inference or fallback is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Choose a model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ModelTier.values().forEach { tier ->
            Text(tier.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(tier.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModelCatalog.modelsFor(tier).forEach { candidate ->
                val downloadCheck = state.eligibilityByModel[candidate.id]
                val runtimeCheck = state.runtimeEligibilityByModel[candidate.id]
                val installed = candidate.id in state.installedModelIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (candidate.id == model.id) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(candidate.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatBytes(candidate.expectedBytes)} · ${candidate.license} · ${candidate.contextTokens}-token context",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (downloadCheck == null) "Download eligibility: checking…"
                            else if (downloadCheck.allowed) "Download: supported on this device"
                            else "Download: blocked · ${downloadCheck.reasons.joinToString(" ")}",
                            color = if (downloadCheck?.allowed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (runtimeCheck == null) "Run now: checking…"
                            else if (runtimeCheck.allowed) "Run now: current RAM and storage checks pass"
                            else "Run now: ${runtimeCheck.reasons.joinToString(" ")}",
                            color = if (runtimeCheck?.allowed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (installed) "Installed · file size present; SHA-256 is checked again before use"
                            else "Not downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.selectModel(candidate.id) },
                            enabled = canSwitchModel || candidate.id == model.id,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (candidate.id == model.id) "Selected" else "Select model") }
                    }
                }
            }
        }
        Text(
            "Download eligibility checks physical RAM and storage. Run-now eligibility also checks current free RAM. Android memory expansion/swap is not treated as physical RAM. A model switch is disabled while inference is loaded or running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${formatBytes(model.expectedBytes)} · ${model.license} · ${model.contextTokens}-token context", style = MaterialTheme.typography.bodySmall)
                Text("Pinned Hugging Face artifact revision; exact byte size and SHA-256 are verified after download.", style = MaterialTheme.typography.bodySmall)
                if (model.id == "phi4-mini-reasoning-int4-4096") {
                    Text(
                        "Compatibility note: the publisher documents a multi-turn cache issue on LiteRT-LM 0.18.x. Dhanam resets this model's conversation after every answer; it will not remember earlier chat turns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                eligibility?.let { detected ->
                    Text(
                        "Android reports: total RAM ${formatBytes(detected.totalRamBytes)} · currently available ${formatBytes(detected.availableRamBytes)} · free app storage ${formatBytes(detected.freeStorageBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Download requires ${formatBytes(model.minTotalRamBytes)} physical total RAM and ${formatBytes(model.minFreeStorageBytes)} free app storage. Running now additionally requires ${formatBytes(model.minAvailableRamBytes)} currently available RAM. Process peak budget: ${formatBytes(model.maxProcessPeakBytes)}. ${model.runtimeRequirement}",
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
                    if (state.preferredModelId == model.id) Text("Saved as preferred model.", style = MaterialTheme.typography.bodySmall)
                    else Text("Set this model as preferred to use it for inference.", style = MaterialTheme.typography.bodySmall)
                }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isDownloading) {
                        OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
                    } else if (!state.isVerified) {
                        if (eligibility?.allowed == true) {
                            Button(onClick = viewModel::download) { Text("Download model") }
                        } else if (eligibility != null) {
                            Text(
                                "Download unavailable on this device. Resource checks must pass first.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = viewModel::refresh) { Text("Recheck") }
                    } else {
                        Button(onClick = viewModel::setPreferred) { Text("Set preferred") }
                        OutlinedButton(onClick = viewModel::deleteModel, enabled = !inferenceState.isReady && !inferenceState.isLoading && !inferenceState.isGenerating) { Text("Remove") }
                    }
                }
                Text(
                    "Inference uses CPU only. GPU/NPU acceleration is not enabled. This is an initial qualification build, not proof that this phone passed a device benchmark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.isVerified) {
            LocalInferenceSection(viewModel = inferenceViewModel, isPreferred = state.preferredModelId == model.id)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MiB"
    val gib = 1024.0 * 1024.0 * 1024.0
    return if (bytes >= gib) {
        String.format(Locale.US, "%.1f GiB", bytes / gib)
    } else {
        String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
    }
}
