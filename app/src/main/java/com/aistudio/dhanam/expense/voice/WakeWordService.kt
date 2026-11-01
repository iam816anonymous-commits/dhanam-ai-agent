package com.aistudio.dhanam.expense.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import com.aistudio.dhanam.expense.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Explicitly user-enabled microphone foreground service.
 *
 * Porcupine owns the microphone while waiting for "Hey Jarvis". Once detected, it releases
 * the microphone and the installed Vosk model or an explicitly permitted Android recognizer
 * converts the following utterance to text. The finance LLM is not loaded by this service.
 */
class WakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycle = WakeWordLifecycleController()
    private var porcupineManager: PorcupineManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var offlineSpeechSession: OfflineVoskSpeechSession? = null
    private var wakeTone: ToneGenerator? = null
    private val speechTimeout = Runnable {
        if (lifecycle.current() == WakeWordLifecycleState.TRANSCRIBING) {
            updateNotification("Speech recognition timed out. Say Hey Jarvis to try again.")
            finishUtteranceAndRearm()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                lifecycle.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (!hasMicrophonePermission()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                    lifecycle.stop()
                    stopSelf()
                    return START_NOT_STICKY
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
                lifecycle.beginStart()
                try {
                    startAsForeground()
                    mainHandler.post { startWakeWordDetection() }
                } catch (error: SecurityException) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                    lifecycle.stop()
                    stopSelf()
                    return START_NOT_STICKY
                } catch (error: RuntimeException) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                    lifecycle.stop()
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startAsForeground() {
        val notification = buildListeningNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startWakeWordDetection() {
        if (porcupineManager != null || lifecycle.current() == WakeWordLifecycleState.TRANSCRIBING) return
        lifecycle.beginStart()
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)) {
            lifecycle.stop()
            stopSelf()
            return
        }
        val key = WakeWordSecrets.read(this)
        val model = WakeWordModelInstaller.modelFile(this)
        if (key.isNullOrBlank() || !model.isFile || model.length() == 0L) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            updateNotification("Hey Jarvis is not configured. Open Dhanam Settings.")
            lifecycle.stop()
            stopSelf()
            return
        }
        try {
            val callback = PorcupineManagerCallback {
                mainHandler.post { onWakeWordDetected() }
            }
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywordPath(model.absolutePath)
                .setSensitivities(floatArrayOf(0.58f))
                .setErrorCallback(PorcupineManagerErrorCallback { error ->
                    mainHandler.post {
                        if (lifecycle.current() == WakeWordLifecycleState.LISTENING) {
                            lifecycle.fail()
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                            updateNotification("Wake-word microphone failed: ${error.message?.take(100) ?: "restart Hey Jarvis"}")
                            stopWakeWordEngine()
                            stopSelf()
                        }
                    }
                })
                .build(this, callback)
            porcupineManager?.start()
            check(lifecycle.beginListening()) { "Wake-word service was not in a startable lifecycle state." }
            updateNotification("Listening for Hey Jarvis · on-device")
        } catch (error: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            lifecycle.fail()
            updateNotification("Wake-word engine could not start: ${error.message?.take(100) ?: "check setup"}")
            stopWakeWordEngine()
            stopSelf()
        }
    }

    private fun onWakeWordDetected() {
        if (!lifecycle.beginTranscription()) return
        stopWakeWordEngine()
        playWakeTone()
        updateNotification("Jarvis heard you · listening for your command")
        mainHandler.postDelayed({ startSpeechToText() }, 250L)
    }

    private fun playWakeTone() {
        runCatching {
            wakeTone?.release()
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            wakeTone = tone
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            mainHandler.postDelayed({
                if (wakeTone === tone) {
                    tone.release()
                    wakeTone = null
                }
            }, 300L)
        }
    }

    private fun startSpeechToText(forceSystemProvider: Boolean = false) {
        if (!hasMicrophonePermission()) {
            updateNotification("Microphone permission was revoked. Enable it in Android app settings.")
            finishUtteranceAndRearm()
            return
        }
        val language = getSharedPreferences("dhanam_voice", MODE_PRIVATE)
            .getString("recognition_language", "en-IN") ?: "en-IN"
        val offlineModel = if (forceSystemProvider) null else OfflineSpeechModelCatalog.installedDirectory(this, language)
        if (offlineModel != null) {
            startOfflineSpeechToText(offlineModel)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("No offline model for this language and no Android speech provider is installed.")
            finishUtteranceAndRearm()
            return
        }
        try {
            val (recognizer, recognitionRoute) = if (forceSystemProvider) {
                SpeechRecognitionPolicy.createSystemRecognizer(this)
            } else {
                SpeechRecognitionPolicy.createRecognizer(this)
            }
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    updateNotification(
                        if (recognitionRoute == SpeechRecognitionRoute.ON_DEVICE)
                            "Jarvis is listening · speech stays on-device"
                        else
                            "Jarvis is listening · online speech fallback is enabled"
                    )
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    if (
                        recognitionRoute == SpeechRecognitionRoute.ON_DEVICE &&
                        SpeechRecognitionPolicy.allowNetworkFallback(this@WakeWordService) &&
                        (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                    ) {
                        updateNotification("The on-device recognizer does not support this language. Using the online provider you explicitly allowed.")
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        mainHandler.postDelayed({
                            if (lifecycle.current() == WakeWordLifecycleState.TRANSCRIBING) startSpeechToText(forceSystemProvider = true)
                        }, 500L)
                        return
                    }
                    updateNotification("Speech recognition failed (code $error). Say Hey Jarvis to retry.")
                    finishUtteranceAndRearm()
                }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) deliverCommand(text)
                    else updateNotification("No speech recognized. Say Hey Jarvis and try again.")
                    finishUtteranceAndRearm()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
            val language = getSharedPreferences("dhanam_voice", MODE_PRIVATE)
                .getString("recognition_language", "en-IN") ?: "en-IN"
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your Dhanam command")
            }
            recognizer.startListening(request)
            mainHandler.removeCallbacks(speechTimeout)
            mainHandler.postDelayed(speechTimeout, SPEECH_TIMEOUT_MS)
        } catch (error: Exception) {
            if (!forceSystemProvider && SpeechRecognitionPolicy.allowNetworkFallback(this) &&
                SpeechRecognizer.isRecognitionAvailable(this)
            ) {
                updateNotification("On-device speech could not start. Using the online provider you explicitly allowed.")
                mainHandler.postDelayed({
                    if (lifecycle.current() == WakeWordLifecycleState.TRANSCRIBING) {
                        startSpeechToText(forceSystemProvider = true)
                    }
                }, 500L)
            } else {
                updateNotification(error.message?.take(140) ?: "Speech recognition could not start. Check speech privacy settings.")
                finishUtteranceAndRearm()
            }
        }
    }

    private fun startOfflineSpeechToText(modelDirectory: java.io.File) {
        updateNotification("Using installed offline speech model · audio stays on this device")
        serviceScope.launch {
            try {
                val session = OfflineVoskSpeechSession.create(this@WakeWordService, modelDirectory)
                if (lifecycle.current() != WakeWordLifecycleState.TRANSCRIBING) {
                    session.cancel()
                    return@launch
                }
                offlineSpeechSession = session
                session.start(object : OfflineVoskSpeechSession.Listener {
                    override fun onPartial(text: String) {
                        updateNotification("Offline speech is processing your command on this device.")
                    }

                    override fun onFinal(text: String) {
                        if (text.isNotBlank()) deliverCommand(text)
                        else updateNotification("No speech recognized. Say Hey Jarvis and try again.")
                        finishUtteranceAndRearm()
                    }

                    override fun onError(message: String) {
                        offlineSpeechSession = null
                        fallbackAfterOfflineFailure(message)
                    }
                })
            } catch (error: Exception) {
                offlineSpeechSession?.cancel()
                offlineSpeechSession = null
                fallbackAfterOfflineFailure(error.message ?: "The offline model could not initialize.")
            }
        }
    }

    private fun fallbackAfterOfflineFailure(message: String) {
        if (SpeechRecognitionPolicy.allowNetworkFallback(this) && SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Offline speech unavailable. Using the online provider you explicitly allowed.")
            mainHandler.postDelayed({
                if (lifecycle.current() == WakeWordLifecycleState.TRANSCRIBING) startSpeechToText(forceSystemProvider = true)
            }, 500L)
        } else {
            updateNotification("Offline speech failed: ${message.take(110)}. Enable online fallback or reinstall the offline model.")
            finishUtteranceAndRearm()
        }
    }

    private fun deliverCommand(transcript: String) {
        val language = getSharedPreferences("dhanam_voice", MODE_PRIVATE)
            .getString("recognition_language", "en-IN") ?: "en-IN"
        sendBroadcast(
            Intent(ACTION_VOICE_COMMAND)
                .setPackage(packageName)
                .putExtra(EXTRA_TRANSCRIPT, transcript)
                .putExtra(EXTRA_LANGUAGE, language)
        )
        updateCommandNotification(transcript, language)
    }

    private fun updateCommandNotification(transcript: String, language: String) {
        val openApp = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_VOICE_COMMAND
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TRANSCRIPT, transcript)
            putExtra(EXTRA_LANGUAGE, language)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            transcript.hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis understood your command")
            .setContentText("Tap to review your command in Dhanam.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your command was transcribed. Tap to review it in Dhanam. No financial changes are saved automatically."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(COMMAND_NOTIFICATION_ID, notification)
    }

    private fun finishUtteranceAndRearm() {
        mainHandler.removeCallbacks(speechTimeout)
        if (!lifecycle.beginRecovery()) return
        val activeOfflineSession = offlineSpeechSession
        offlineSpeechSession = null
        activeOfflineSession?.cancel()
        val activeRecognizer = speechRecognizer
        speechRecognizer = null
        activeRecognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)) {
            mainHandler.postDelayed({ startWakeWordDetection() }, REARM_DELAY_MS)
        } else {
            lifecycle.stop()
        }
    }

    private fun stopWakeWordEngine() {
        porcupineManager?.let {
            runCatching { it.stop() }
            runCatching { it.delete() }
        }
        porcupineManager = null
    }

    private fun buildListeningNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Dhanam · Hey Jarvis enabled")
            .setContentText("On-device wake-word detection is active.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(message: String) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Dhanam · Hey Jarvis")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey Jarvis voice assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Visible notification while Hey Jarvis wake-word listening is enabled." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        lifecycle.stop()
        serviceScope.cancel()
        offlineSpeechSession?.cancel()
        offlineSpeechSession = null
        stopWakeWordEngine()
        speechRecognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        speechRecognizer = null
        wakeTone?.release()
        wakeTone = null
        getSystemService(NotificationManager::class.java).cancel(COMMAND_NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.aistudio.dhanam.expense.voice.START_WAKE_WORD"
        const val ACTION_STOP = "com.aistudio.dhanam.expense.voice.STOP_WAKE_WORD"
        const val ACTION_VOICE_COMMAND = "com.aistudio.dhanam.expense.voice.VOICE_COMMAND"
        const val ACTION_OPEN_VOICE_COMMAND = "com.aistudio.dhanam.expense.voice.OPEN_VOICE_COMMAND"
        const val EXTRA_TRANSCRIPT = "voice_transcript"
        const val EXTRA_LANGUAGE = "voice_language"
        const val PREFS = "dhanam_wake_word"
        const val KEY_ENABLED = "enabled"
        const val COMMAND_NOTIFICATION_ID = 7042
        private const val CHANNEL_ID = "dhanam_hey_jarvis"
        private const val NOTIFICATION_ID = 7041
        private const val REARM_DELAY_MS = 1200L
        private const val SPEECH_TIMEOUT_MS = 20_000L
    }
}
