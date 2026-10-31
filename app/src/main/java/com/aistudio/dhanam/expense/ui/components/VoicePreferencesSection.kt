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
    var permissionGranted by remember(context) {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var recognitionAvailable by remember(context) { mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context)) }
    var onDeviceAvailable by remember(context) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "recognition_language" -> {
                    language = preferences.getString("recognition_language", "en-IN") ?: "en-IN"
                }
                "voice_feedback_enabled" -> {
                    voiceReplies = preferences.getBoolean("voice_feedback_enabled", false)
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun refreshDiagnostics() {
        permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
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
            Text(
                "Speech service: " + (if (recognitionAvailable) "Available" else "Not found"),
                color = if (recognitionAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                when {
                    onDeviceAvailable -> "Dedicated on-device recognizer: available. Language support is checked when you start recognition."
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "Dedicated on-device recognizer: unavailable. The system recognizer may use network services."
                    else -> "Android 8–11: the system recognizer accepts an offline preference, but Android cannot guarantee offline processing through this API."
                },
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
            Text(
                "For Android 8.1, test voice in airplane mode after installing English/Telugu offline speech packs in the system speech engine. If it fails offline, this Android version needs a dedicated downloadable speech-recognition model; the finance LLM download does not provide speech recognition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
