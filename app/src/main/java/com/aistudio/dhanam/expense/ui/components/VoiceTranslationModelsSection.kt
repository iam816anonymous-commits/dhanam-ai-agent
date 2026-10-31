package com.aistudio.dhanam.expense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VoiceTranslationModelsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyLanguage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Checking installed translation models…") }

    suspend fun refreshModels() {
        try {
            downloaded = withContext(Dispatchers.IO) {
                Tasks.await(
                    RemoteModelManager.getInstance()
                        .getDownloadedModels(TranslateRemoteModel::class.java)
                ).map { it.language }.toSet()
            }
            status = "Translation model status updated."
        } catch (error: Exception) {
            status = "Could not check translation models: ${error.message ?: "Google Play services unavailable"}"
        }
    }

    LaunchedEffect(context) { refreshModels() }

    fun download(language: String, label: String) {
        if (busyLanguage != null) return
        busyLanguage = language
        status = "Downloading $label translation model. Keep Dhanam open and use Wi-Fi."
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val model = TranslateRemoteModel.Builder(language).build()
                    val conditions = DownloadConditions.Builder().requireWifi().build()
                    Tasks.await(RemoteModelManager.getInstance().download(model, conditions))
                }
                refreshModels()
                status = "$label translation model downloaded. Translation can now run offline."
            } catch (error: Exception) {
                status = "$label model download failed: ${error.cause?.message ?: error.message ?: "Unknown error"}. Check Wi-Fi and Google Play services, then retry."
            } finally {
                busyLanguage = null
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Translation models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Dhanam identifies the language of the recognized transcript and translates Telugu or Hindi into English on-device. Download each language model once while online; translation itself then works offline. Models are roughly 30 MB each. Downloads are user-initiated and Wi-Fi-only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(
                Triple("te", "Telugu", "Telugu → English"),
                Triple("hi", "Hindi", "Hindi → English")
            ).forEach { (language, label, direction) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(direction, fontWeight = FontWeight.Medium)
                        Text(
                            if (language in downloaded) "Installed · ready for offline translation" else "Not installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (language in downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (language in downloaded) {
                        OutlinedButton(onClick = { scope.launch { refreshModels() } }, enabled = busyLanguage == null) {
                            Text("Refresh")
                        }
                    } else {
                        Button(onClick = { download(language, label) }, enabled = busyLanguage == null) {
                            Text(if (busyLanguage == language) "Downloading…" else "Download")
                        }
                    }
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Speech recognition is a separate Android service. These models translate recognized text; they do not provide offline speech-to-text. Android 8.1 may still require an installed speech engine with offline language packs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
