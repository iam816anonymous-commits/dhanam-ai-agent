package com.aistudio.dhanam.expense.ui.components

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aistudio.dhanam.expense.voice.OfflineSpeechModelCatalog
import com.aistudio.dhanam.expense.voice.OfflineSpeechModelInstaller
import com.aistudio.dhanam.expense.voice.OfflineSpeechModelSpec
import kotlinx.coroutines.launch

@Composable
private fun OfflineSpeechModelRow(
    contextLabel: String,
    spec: OfflineSpeechModelSpec,
    installed: Boolean,
    selected: Boolean,
    downloading: Boolean,
    progress: Int,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(contextLabel, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${spec.approximateDownloadBytes / (1024L * 1024L)} MB · ${spec.accuracyNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            downloading -> Text("$progress%", style = MaterialTheme.typography.labelMedium)
            selected -> Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            installed -> OutlinedButton(onClick = onSelect) { Text("Use") }
            else -> OutlinedButton(onClick = onDownload, enabled = downloadEnabled) { Text("Download") }
        }
    }
}

/** Persistent voice preferences plus live Android microphone/speech-service diagnostics. */
@Composable
fun VoicePreferencesSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    var language by remember(context) {
        mutableStateOf(preferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    var voiceReplies by remember(context) {
        mutableStateOf(preferences.getBoolean("voice_feedback_enabled", false))
    }
    var allowNetworkFallback by remember(context) {
        mutableStateOf(preferences.getBoolean("allow_network_speech_recognition", false))
    }
    var permissionGranted by remember(context) {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var recognitionAvailable by remember(context) { mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context)) }
    var onDeviceAvailable by remember(context) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false))
    }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var installedModels by remember(context) {
        mutableStateOf(OfflineSpeechModelCatalog.models.filter { OfflineSpeechModelCatalog.isInstalled(context, it) }.map { it.id }.toSet())
    }
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var modelMessage by remember { mutableStateOf<String?>(null) }
    val modelScope = rememberCoroutineScope()

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "recognition_language" -> {
                    language = preferences.getString("recognition_language", "en-IN") ?: "en-IN"
                }
                "voice_feedback_enabled" -> {
                    voiceReplies = preferences.getBoolean("voice_feedback_enabled", false)
                }
                "allow_network_speech_recognition" -> {
                    allowNetworkFallback = preferences.getBoolean("allow_network_speech_recognition", false)
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun refreshDiagnostics() {
        permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionMessage = if (granted) "Microphone permission granted." else
            "Microphone permission denied. Enable it in Android app settings to use voice."
        refreshDiagnostics()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshDiagnostics()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice and language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Dhanam uses Android speech recognition for voice input and the phone's installed text-to-speech engine for optional replies. Voice recognition is separate from the downloaded finance LLM models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("en-IN" to "English", "te-IN" to "తెలుగు", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = {
                            language = code
                            preferences.edit().putString("recognition_language", code).apply()
                        },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "Microphone permission: " + (if (permissionGranted) "Granted" else "Not granted"),
                color = if (permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            val selectedModelInstalled = OfflineSpeechModelCatalog.selectedModelId(context, language) != null
            Text(
                "Speech service: " + when {
                    selectedModelInstalled -> "Offline Vosk model installed for $language"
                    onDeviceAvailable -> "Android on-device provider available"
                    recognitionAvailable && allowNetworkFallback -> "Android provider available (online fallback allowed)"
                    recognitionAvailable -> "Android provider found, but online fallback is disabled"
                    else -> "No speech engine available for the selected language"
                },
                color = if (selectedModelInstalled || onDeviceAvailable || (recognitionAvailable && allowNetworkFallback)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                when {
                    onDeviceAvailable -> "Dedicated on-device recognizer: available. Language support is checked when you start recognition."
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "Dedicated on-device recognizer: unavailable. The system recognizer may use network services."
                    else -> "Android 8–11: install a Vosk model below for guaranteed local speech-to-text. The built-in Android speech API cannot guarantee offline processing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Allow online speech fallback", fontWeight = FontWeight.Medium)
                    Text(
                        "When a guaranteed on-device recognizer is unavailable, allow Android's system speech provider. It may send microphone audio to a remote service. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allowNetworkFallback,
                    onCheckedChange = { enabled ->
                        allowNetworkFallback = enabled
                        preferences.edit().putBoolean("allow_network_speech_recognition", enabled).apply()
                    }
                )
            }
            Text(
                if (allowNetworkFallback) "Remote speech processing is permitted as a fallback. Dhanam still does not send your finance ledger to a cloud LLM."
                else "Remote speech processing is blocked by Dhanam when no dedicated on-device recognizer is available. Android 8.1 does not provide a guaranteed offline-only SpeechRecognizer API.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            permissionMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!permissionGranted) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant microphone permission")
                }
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {
                        permissionMessage = "Open Android Settings → Apps → Dhanam → Permissions → Microphone."
                    }
                }) { Text("Open app permissions") }
            }
            Text("Download offline speech models", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "These are separate from the finance LLMs. Download only the languages you use. Once installed, transcription runs locally and does not need internet access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OfflineSpeechModelCatalog.models.forEach { spec ->
                OfflineSpeechModelRow(
                    contextLabel = spec.displayName,
                    spec = spec,
                    installed = spec.id in installedModels,
                    selected = OfflineSpeechModelCatalog.selectedModelId(context, spec.languageTag) == spec.id,
                    downloading = downloadingModelId == spec.id,
                    progress = if (downloadingModelId == spec.id) downloadProgress else 0,
                    downloadEnabled = downloadingModelId == null,
                    onDownload = {
                        downloadingModelId = spec.id
                        downloadProgress = 0
                        modelMessage = "Downloading ${spec.displayName}…"
                        modelScope.launch {
                            try {
                                OfflineSpeechModelInstaller.install(context, spec) { percent ->
                                    downloadProgress = percent
                                }
                                installedModels = installedModels + spec.id
                                OfflineSpeechModelCatalog.selectModel(context, spec)
                                modelMessage = "${spec.displayName} installed and selected for ${spec.languageTag}."
                            } catch (error: Exception) {
                                modelMessage = error.message ?: "Could not install ${spec.displayName}."
                            } finally {
                                downloadingModelId = null
                            }
                        }
                    },
                    onSelect = {
                        OfflineSpeechModelCatalog.selectModel(context, spec)
                        modelMessage = "${spec.displayName} selected for ${spec.languageTag}."
                    }
                )
            }
            if (language.startsWith("te", ignoreCase = true)) {
                Text(
                    "Telugu offline model: no reliable small Vosk model is offered. The published small Telugu model reports 87.9 word error rate, so it is deliberately not offered for financial commands. Use Android's dedicated on-device recognizer if it supports Telugu, or enable online fallback explicitly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            modelMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Speak assistant replies", fontWeight = FontWeight.Medium)
                    Text("Uses the phone's installed text-to-speech voice.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = voiceReplies,
                    onCheckedChange = {
                        voiceReplies = it
                        preferences.edit().putBoolean("voice_feedback_enabled", it).apply()
                    }
                )
            }
        }
    }
}
