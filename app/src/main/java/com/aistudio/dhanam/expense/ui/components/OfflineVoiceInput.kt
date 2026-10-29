package com.aistudio.dhanam.expense.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Offline-only speech input. Never falls back to a general recognizer that may upload audio.
 * On Android versions without the on-device SpeechRecognizer API, text entry remains available.
 */
@Composable
fun OfflineVoiceInput(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    var language by remember { mutableStateOf("en-IN") }
    var status by remember { mutableStateOf("Voice input uses the device's on-device recognizer only.") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val startListening: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            status = "Offline voice input requires Android 12+ with an on-device speech recognizer. You can type instead."
        } else if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            status = "No on-device speech recognizer is available. Audio was not sent to an online recognizer."
        } else {
            try {
                recognizer?.destroy()
                val instance = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer = instance
                instance.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { status = "Listening offline…" }
                    override fun onBeginningOfSpeech() { status = "Listening offline…" }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { status = "Processing speech on-device…" }
                    override fun onError(error: Int) {
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Try again or type your request."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This on-device recognizer does not support the selected language."
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The selected offline language pack is unavailable."
                            else -> "Offline speech recognition failed (code $error). You can type instead."
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        if (spoken.isNotBlank()) {
                            latestOnRecognized(spoken)
                            status = "Speech captured locally. Review the text before sending."
                        } else {
                            status = "No speech was recognized. Try again or type your request."
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                instance.startListening(intent)
                status = "Starting on-device speech recognition…"
            } catch (error: Exception) {
                status = "Could not start offline voice input. You can type instead."
                recognizer?.destroy()
                recognizer = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else status = "Microphone permission was denied. Nothing was recorded."
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recognizer?.cancel() } catch (_: Exception) { }
            try { recognizer?.destroy() } catch (_: Exception) { }
        }
    }

    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Voice language", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = enabled, onClick = { language = "en-IN" }) {
                Text(if (language == "en-IN") "✓ English" else "English")
            }
            TextButton(enabled = enabled, onClick = { language = "te-IN" }) {
                Text(if (language == "te-IN") "✓ తెలుగు" else "తెలుగు")
            }
            Button(enabled = enabled, onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) {
                Text("Speak offline")
            }
        }
        Text(status, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
