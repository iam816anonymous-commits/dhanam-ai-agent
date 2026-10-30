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
 * Privacy-aware speech input. Android 12+ uses the dedicated on-device recognizer.
 * Android 8–11 use the installed system recognizer with EXTRA_PREFER_OFFLINE; that is a
 * preference, not a platform guarantee, so the UI warns users that the service may ignore it.
 */
@Composable
fun OfflineVoiceInput(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    val voicePreferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    var language by remember {
        mutableStateOf(voicePreferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    fun selectLanguage(code: String) {
        language = code
        voicePreferences.edit().putString("recognition_language", code).apply()
        status = "Voice language set to ${when (code) { "te-IN" -> "Telugu"; "hi-IN" -> "Hindi"; else -> "English" }}."
    }
    var status by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Voice uses the dedicated on-device recognizer when available."
            } else {
                "Android 8–11 use the system speech service with offline preference; the service may ignore it."
            }
        )
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val startListening: () -> Unit = {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
            val onDeviceApiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (onDeviceApiAvailable && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                status = "No dedicated on-device recognizer is available. Install an offline speech pack or type your request."
            } else {
                // Android 12+ has a dedicated on-device recognizer API. Android 8–11 do not:
                // use the platform recognizer with EXTRA_PREFER_OFFLINE for devices that have
                // downloaded an offline language pack. On those OS versions this flag is a
                // preference, not a guarantee; the status explicitly tells the user this.
                val instance = if (onDeviceApiAvailable) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                recognizer = instance
                instance.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        status = if (onDeviceApiAvailable) "Listening offline…" else "Listening with offline preference…"
                    }
                    override fun onBeginningOfSpeech() {
                        status = if (onDeviceApiAvailable) "Listening offline…" else "Listening with offline preference…"
                    }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { status = "Processing speech…" }
                    override fun onError(error: Int) {
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Check that an offline language pack is installed, then try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This speech engine does not support the selected language."
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The selected language pack is unavailable offline."
                            else -> "Speech recognition failed (code $error). Check the installed language pack or type instead."
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        if (spoken.isNotBlank()) {
                            latestOnRecognized(spoken)
                            status = if (onDeviceApiAvailable) {
                                "Speech captured on-device. Review the text before sending."
                            } else {
                                "Speech captured. Android 8–11 only request offline recognition; the system speech service may ignore that preference. Review the text before sending."
                            }
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
                status = if (onDeviceApiAvailable) {
                    "Starting on-device speech recognition…"
                } else {
                    "Starting Android system speech recognition with offline preference. Offline mode depends on the installed speech engine and language pack."
                }
            }
        } catch (error: Exception) {
            status = "Could not start speech recognition. Check the installed speech engine or type instead."
            try { recognizer?.destroy() } catch (_: Exception) { }
            recognizer = null
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
            TextButton(enabled = enabled, onClick = { selectLanguage("en-IN") }) {
                Text(if (language == "en-IN") "✓ English" else "English")
            }
            TextButton(enabled = enabled, onClick = { selectLanguage("te-IN") }) {
                Text(if (language == "te-IN") "✓ తెలుగు" else "తెలుగు")
            }
            TextButton(enabled = enabled, onClick = { selectLanguage("hi-IN") }) {
                Text(if (language == "hi-IN") "✓ हिन्दी" else "हिन्दी")
            }
            Button(enabled = enabled, onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) {
                Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Speak offline" else "Speak (offline preferred)")
            }
        }
        Text(status, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
