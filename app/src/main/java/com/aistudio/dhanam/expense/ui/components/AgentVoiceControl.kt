package com.aistudio.dhanam.expense.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference

/**
 * One in-app voice path for the floating assistant.
 * API 31+ uses Android's dedicated on-device recognizer when installed. Older Android
 * versions use the platform recognizer with EXTRA_PREFER_OFFLINE; Android 8.1 cannot
 * guarantee offline recognition through the public platform API.
 * Speech is editable before submission; financial writes still require explicit confirmation.
 */
@Composable
fun AgentVoiceControl(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    val preferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    var language by remember(context) {
        mutableStateOf(preferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    var showDialog by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val recognizerRef = remember { AtomicReference<SpeechRecognizer?>(null) }

    fun releaseRecognizer(cancel: Boolean) {
        val active = recognizerRef.getAndSet(null) ?: return
        try { if (cancel) active.cancel() } catch (_: Exception) { }
        try { active.destroy() } catch (_: Exception) { }
    }

    fun closeDialog() {
        releaseRecognizer(cancel = true)
        isListening = false
        isProcessing = false
        showDialog = false
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is required. Grant it in Android app permissions and try again."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "No Android speech-recognition service is installed or enabled. Install or enable one, then retry."
            isListening = false
            isProcessing = false
            return
        }
        releaseRecognizer(cancel = true)
        transcript = ""
        status = "Preparing the speech service…"
        try {
            val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            val instance = if (onDeviceAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizerRef.set(instance)
            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    isProcessing = false
                    status = if (onDeviceAvailable) "On-device recognizer ready. Speak now."
                    else "Speech service ready. Offline is requested but not guaranteed on this Android version."
                }
                override fun onBeginningOfSpeech() {
                    isListening = true
                    isProcessing = false
                    status = if (onDeviceAvailable) "Listening on-device…" else "Listening… Android's system service may require network access."
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isListening = false
                    isProcessing = true
                    status = "Processing speech…"
                }
                override fun onError(error: Int) {
                    if (recognizerRef.get() !== instance) return
                    isListening = false
                    isProcessing = false
                    status = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check the microphone and try again."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech session was interrupted. Retry once the microphone is available."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android denied microphone access. Enable Microphone in app permissions."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "The speech service could not reach its recognition service. Check its offline language pack or connection."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No words were recognised. Speak clearly and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Wait a moment and retry."
                        SpeechRecognizer.ERROR_SERVER -> "The speech service returned an error. Check the installed speech engine."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected. Tap Start listening and speak."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "The installed speech engine does not support this language."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The selected language pack is not installed or available offline."
                        else -> "Speech recognition failed (error $error). Check the speech engine and language pack."
                    }
                    releaseRecognizer(cancel = false)
                }
                override fun onResults(results: Bundle?) {
                    if (recognizerRef.get() !== instance) return
                    isListening = false
                    isProcessing = false
                    transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    status = if (transcript.isBlank()) "No speech was captured. Try again or type your request."
                    else "Review and correct the transcript before sending it to Dhanam."
                    releaseRecognizer(cancel = false)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (recognizerRef.get() !== instance) return
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        transcript = partial
                        status = "Hearing: $partial"
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            instance.startListening(intent)
            isListening = true
            isProcessing = false
            status = when {
                onDeviceAvailable -> "Starting dedicated on-device recognition…"
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                    "Starting Android system recognition. Offline is only a preference on Android 8–11; test with airplane mode."
                else -> "No dedicated on-device recognizer is installed. Using the system recognizer; offline use is not guaranteed."
            }
        } catch (_: SecurityException) {
            releaseRecognizer(cancel = true)
            isListening = false
            isProcessing = false
            status = "Android denied microphone access. Enable Microphone in app permissions."
        } catch (_: IllegalStateException) {
            releaseRecognizer(cancel = true)
            isListening = false
            isProcessing = false
            status = "The speech service is already busy or unavailable. Wait briefly and retry."
        } catch (error: Exception) {
            releaseRecognizer(cancel = true)
            isListening = false
            isProcessing = false
            status = error.message?.take(180) ?: "Could not start speech recognition. Check microphone permission and the installed speech service."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else {
            isListening = false
            isProcessing = false
            status = "Microphone permission was denied. Enable it in Android app permissions to use voice."
        }
    }

    DisposableEffect(context) {
        onDispose {
            val active = recognizerRef.getAndSet(null)
            try { active?.cancel() } catch (_: Exception) { }
            try { active?.destroy() } catch (_: Exception) { }
        }
    }

    FilledTonalButton(
        onClick = { status = null; transcript = ""; showDialog = true },
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(Icons.Default.Mic, contentDescription = "Voice input")
        Text("Speak", modifier = Modifier.padding(start = 5.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = ::closeDialog,
            title = { Text("Dhanam voice input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a language and speak a finance request. Android 8.1 uses the installed system speech service with an offline preference; it cannot guarantee that recognition stays offline.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en-IN" to "English", "te-IN" to "తెలుగు", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                            FilterChip(
                                selected = language == code,
                                enabled = !isListening && !isProcessing,
                                onClick = {
                                    language = code
                                    preferences.edit().putString("recognition_language", code).apply()
                                    transcript = ""
                                    status = "Voice language set to $label."
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (transcript.isNotBlank()) {
                        OutlinedTextField(
                            value = transcript,
                            onValueChange = { transcript = it.take(1000) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Review transcript") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    status?.let {
                        Text(it, color = if (it.contains("denied", true) || it.contains("failed", true) || it.contains("error", true))
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                when {
                    isListening -> Button(onClick = {
                        isListening = false
                        isProcessing = true
                        status = "Finishing speech capture…"
                        try { recognizerRef.get()?.stopListening() }
                        catch (_: Exception) {
                            releaseRecognizer(cancel = true)
                            isProcessing = false
                            status = "Could not finish the speech session. Try again."
                        }
                    }) { Text("Finish") }
                    isProcessing -> Button(enabled = false, onClick = {}) { Text("Processing…") }
                    transcript.isNotBlank() -> Button(onClick = {
                        val reviewed = transcript.trim()
                        if (reviewed.isNotBlank()) latestOnRecognized(reviewed)
                        closeDialog()
                    }) { Text("Use transcript") }
                    else -> Button(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Start listening") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            status = "Open Android Settings → Apps → Dhanam → Permissions to enable Microphone."
                        }
                    }) { Text("App permissions") }
                    TextButton(onClick = ::closeDialog) { Text("Cancel") }
                }
            }
        )
    }
}
