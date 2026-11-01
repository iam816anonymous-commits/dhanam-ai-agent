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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aistudio.dhanam.expense.voice.SpeechRecognitionPolicy
import com.aistudio.dhanam.expense.voice.OfflineSpeechModelCatalog
import com.aistudio.dhanam.expense.voice.OfflineVoskSpeechSession
import java.util.concurrent.atomic.AtomicReference

/**
 * One in-app voice path for the floating assistant.
 * An installed Vosk model is preferred and runs fully offline. Without one, API 31+ uses
 * Android's dedicated on-device recognizer when available. The remote-capable system provider
 * is used only after explicit user consent. Speech remains editable before submission.
 */
@Composable
fun AgentVoiceControl(
    enabled: Boolean,
    onRecognized: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    val preferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    val dedicatedOnDeviceAvailable = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
    var language by remember(context) {
        mutableStateOf(preferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    var showDialog by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var translatedTranscript by remember { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }
    val translationScope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val recognizerRef = remember { AtomicReference<SpeechRecognizer?>(null) }
    val offlineSessionRef = remember { AtomicReference<OfflineVoskSpeechSession?>(null) }

    fun releaseRecognizer(cancel: Boolean) {
        val offline = offlineSessionRef.getAndSet(null)
        if (cancel) offline?.cancel()
        val active = recognizerRef.getAndSet(null)
        if (active != null) {
            try { if (cancel) active.cancel() } catch (_: Exception) { }
            try { active.destroy() } catch (_: Exception) { }
        }
    }

    fun closeDialog() {
        releaseRecognizer(cancel = true)
        isListening = false
        isProcessing = false
        showDialog = false
    }

    fun startListening(forceSystemProvider: Boolean = false, skipOfflineModel: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is required. Grant it in Android app permissions and try again."
            return
        }
        val offlineModel = if (forceSystemProvider || skipOfflineModel) null else OfflineSpeechModelCatalog.installedDirectory(context, language)
        if (offlineModel == null && !SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "No offline model for $language is installed and no Android speech provider is available."
            isListening = false
            isProcessing = false
            return
        }
        releaseRecognizer(cancel = true)
        transcript = ""
        translatedTranscript = ""
        status = "Preparing the speech service…"
        if (offlineModel != null) {
            isListening = false
            isProcessing = true
            status = "Loading the installed offline speech model. Audio stays on this device."
            translationScope.launch {
                try {
                    val session = OfflineVoskSpeechSession.create(context, offlineModel)
                    if (!showDialog) {
                        session.cancel()
                        return@launch
                    }
                    offlineSessionRef.set(session)
                    session.start(object : OfflineVoskSpeechSession.Listener {
                        override fun onPartial(text: String) {
                            transcript = text
                            isListening = true
                            isProcessing = false
                            status = "Offline recognition · hearing: $text"
                        }

                        override fun onFinal(text: String) {
                            offlineSessionRef.compareAndSet(session, null)
                            if (text.isBlank()) {
                                val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
                                if (onDeviceAvailable) {
                                    status = "Offline model returned no words. Trying Android's dedicated on-device recognizer."
                                    translationScope.launch {
                                        delay(500)
                                        startListening(skipOfflineModel = true)
                                    }
                                } else if (SpeechRecognitionPolicy.allowNetworkFallback(context)) {
                                    status = "Offline model returned no words. Trying the online provider you explicitly allowed."
                                    translationScope.launch {
                                        delay(500)
                                        startListening(forceSystemProvider = true)
                                    }
                                } else {
                                    isListening = false
                                    isProcessing = false
                                    status = "No speech was recognized. Try again or type your request."
                                }
                            } else {
                                transcript = text
                                isListening = false
                                isProcessing = false
                                status = "Transcribed offline. Review and correct the text before sending."
                            }
                        }

                        override fun onError(message: String) {
                            offlineSessionRef.compareAndSet(session, null)
                            val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
                            if (onDeviceAvailable) {
                                status = "Offline recognition failed. Trying Android's dedicated on-device recognizer."
                                translationScope.launch {
                                    delay(500)
                                    startListening(skipOfflineModel = true)
                                }
                            } else if (SpeechRecognitionPolicy.allowNetworkFallback(context)) {
                                status = "Offline recognition failed. Trying the online provider you explicitly allowed."
                                translationScope.launch {
                                    delay(500)
                                    startListening(forceSystemProvider = true)
                                }
                            } else {
                                isListening = false
                                isProcessing = false
                                status = message
                            }
                        }
                    })
                    isListening = true
                    isProcessing = false
                    status = "Listening offline. Speak your command."
                } catch (cancel: CancellationException) {
                    offlineSessionRef.getAndSet(null)?.cancel()
                    throw cancel
                } catch (error: Exception) {
                    offlineSessionRef.getAndSet(null)?.cancel()
                    if (!showDialog) return@launch
                    val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
                    if (onDeviceAvailable) {
                        status = "Offline speech could not start. Trying Android's dedicated on-device recognizer."
                        delay(500)
                        startListening(skipOfflineModel = true)
                    } else if (SpeechRecognitionPolicy.allowNetworkFallback(context)) {
                        status = "Offline speech could not start. Trying the online provider you explicitly allowed."
                        delay(500)
                        startListening(forceSystemProvider = true)
                    } else {
                        isListening = false
                        isProcessing = false
                        status = error.message ?: "Offline speech recognition could not start."
                    }
                }
            }
            return
        }
        try {
            val (instance, recognitionRoute) = if (forceSystemProvider) {
                SpeechRecognitionPolicy.createSystemRecognizer(context)
            } else {
                SpeechRecognitionPolicy.createRecognizer(context)
            }
            val onDeviceAvailable = recognitionRoute == com.aistudio.dhanam.expense.voice.SpeechRecognitionRoute.ON_DEVICE
            recognizerRef.set(instance)
            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    isProcessing = false
                    status = if (onDeviceAvailable) "On-device recognizer ready. Speak now."
                    else "System speech provider ready. Remote processing may occur."
                }
                override fun onBeginningOfSpeech() {
                    isListening = true
                    isProcessing = false
                    status = if (onDeviceAvailable) "Listening on-device…" else "Listening through the Android system provider. Remote processing may occur."
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
                    if (
                        onDeviceAvailable &&
                        SpeechRecognitionPolicy.allowNetworkFallback(context) &&
                        (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                    ) {
                        status = "The on-device recognizer does not support this language. Trying the online provider you explicitly allowed."
                        releaseRecognizer(cancel = true)
                        translationScope.launch {
                            delay(500)
                            startListening(forceSystemProvider = true)
                        }
                        return
                    }
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
                else -> "Using the Android system provider. Remote processing may occur because you enabled the online fallback."
            }
        } catch (_: SecurityException) {
            releaseRecognizer(cancel = true)
            isListening = false
            isProcessing = false
            status = "Android denied microphone access. Enable Microphone in app permissions."
        } catch (error: IllegalStateException) {
            releaseRecognizer(cancel = true)
            if (!forceSystemProvider && SpeechRecognitionPolicy.allowNetworkFallback(context)) {
                status = "On-device speech could not start. Trying the online provider you explicitly allowed."
                translationScope.launch {
                    delay(500)
                    startListening(forceSystemProvider = true)
                }
            } else {
                isListening = false
                isProcessing = false
                status = error.message ?: "The speech service is already busy or unavailable. Wait briefly and retry."
            }
        } catch (error: Exception) {
            releaseRecognizer(cancel = true)
            if (!forceSystemProvider && SpeechRecognitionPolicy.allowNetworkFallback(context)) {
                status = "On-device speech could not start. Trying the online provider you explicitly allowed."
                translationScope.launch {
                    delay(500)
                    startListening(forceSystemProvider = true)
                }
            } else {
                isListening = false
                isProcessing = false
                status = error.message?.take(180) ?: "Could not start speech recognition. Check microphone permission and the installed speech service."
            }
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

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recognizerRef.get() != null) {
                releaseRecognizer(cancel = true)
                isListening = false
                isProcessing = false
                status = "Voice capture stopped because Dhanam moved to the background. Tap Start listening to retry."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseRecognizer(cancel = true)
        }
    }

    FilledTonalButton(
        onClick = {
            language = preferences.getString("recognition_language", "en-IN") ?: "en-IN"
            status = null
            transcript = ""
            translatedTranscript = ""
            showDialog = true
        },
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
                    Text(
                        when {
                            dedicatedOnDeviceAvailable -> "A dedicated on-device recognizer is available. The selected language pack is checked when recognition starts."
                            SpeechRecognitionPolicy.allowNetworkFallback(context) -> "No dedicated on-device recognizer is available. You explicitly allowed the Android system provider, which may send audio to a remote service."
                            else -> "No guaranteed on-device recognizer is available. Online speech processing is blocked until you explicitly enable it in Settings."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en-IN" to "English", "te-IN" to "తెలుగు", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                            FilterChip(
                                selected = language == code,
                                enabled = !isListening && !isProcessing,
                                onClick = {
                                    language = code
                                    preferences.edit().putString("recognition_language", code).apply()
                                    transcript = ""
                                    translatedTranscript = ""
                                    status = "Voice language set to $label."
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (transcript.isNotBlank()) {
                        OutlinedTextField(
                            value = transcript,
                            onValueChange = { transcript = it.take(1000); translatedTranscript = "" },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Review transcript") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    if (translatedTranscript.isNotBlank()) {
                        Text("English interpretation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(translatedTranscript, style = MaterialTheme.typography.bodyMedium)
                    }
                    status?.let {
                        Text(it, color = if (it.contains("denied", true) || it.contains("failed", true) || it.contains("error", true) || it.contains("not installed", true))
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
                        try {
                            offlineSessionRef.get()?.stop() ?: recognizerRef.get()?.stopListening()
                        } catch (_: Exception) {
                            releaseRecognizer(cancel = true)
                            isProcessing = false
                            status = "Could not finish the speech session. Try again."
                        }
                    }) { Text("Finish") }
                    isProcessing || translating -> Button(enabled = false, onClick = {}) { Text(if (translating) "Translating…" else "Processing…") }
                    translatedTranscript.isNotBlank() -> Button(onClick = {
                        val original = transcript.trim()
                        val normalized = translatedTranscript.trim()
                        if (original.isNotBlank() && normalized.isNotBlank()) latestOnRecognized(original, normalized)
                        closeDialog()
                    }) { Text("Send English to Dhanam") }
                    transcript.isNotBlank() -> Button(onClick = {
                        translating = true
                        status = "Identifying language and translating on-device…"
                        translationScope.launch {
                            try {
                                val result = VoiceTranslationEngine.translateToEnglish(transcript, language)
                                translatedTranscript = result.normalizedText
                                status = if (result.wasTranslated) "${result.sourceLanguage} translated on-device. Check the English interpretation before sending."
                                else "English detected. Review the transcript before sending."
                            } catch (error: Exception) {
                                translatedTranscript = ""
                                status = error.message ?: "Translation failed. Your original transcript is still available; nothing was sent."
                            } finally {
                                translating = false
                            }
                        }
                    }, enabled = !translating) { Text(if (translating) "Translating…" else "Translate to English") }
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
