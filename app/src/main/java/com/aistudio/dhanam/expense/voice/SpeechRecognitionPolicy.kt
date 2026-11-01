package com.aistudio.dhanam.expense.voice

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer

enum class SpeechRecognitionRoute {
    OFFLINE_VOSK_MODEL,
    ON_DEVICE,
    SYSTEM_PROVIDER_NETWORK_CONSENT,
    BLOCKED
}

/**
 * Vosk supplies a real offline route on API 27+. Android's SpeechRecognizer is not a guaranteed
 * offline engine on API 27–30, and EXTRA_PREFER_OFFLINE may be ignored. The remote-capable
 * system provider is therefore used only after explicit user consent.
 */
object SpeechRecognitionPolicy {
    const val PREFS = "dhanam_voice"
    const val KEY_ALLOW_NETWORK = "allow_network_speech_recognition"

    fun allowNetworkFallback(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_NETWORK, false)

    fun route(
        sdkInt: Int,
        onDeviceRecognizerAvailable: Boolean,
        allowNetworkFallback: Boolean,
        offlineModelInstalled: Boolean = false
    ): SpeechRecognitionRoute = when {
        offlineModelInstalled -> SpeechRecognitionRoute.OFFLINE_VOSK_MODEL
        sdkInt >= Build.VERSION_CODES.S && onDeviceRecognizerAvailable ->
            SpeechRecognitionRoute.ON_DEVICE
        allowNetworkFallback -> SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT
        else -> SpeechRecognitionRoute.BLOCKED
    }

    fun createRecognizer(context: Context): Pair<SpeechRecognizer, SpeechRecognitionRoute> {
        check(SpeechRecognizer.isRecognitionAvailable(context)) {
            "No Android speech-recognition provider is installed or enabled."
        }
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val route = route(
            Build.VERSION.SDK_INT,
            onDeviceAvailable,
            allowNetworkFallback(context)
        )
        val recognizer = when (route) {
            SpeechRecognitionRoute.OFFLINE_VOSK_MODEL -> throw IllegalStateException(
                "Use the installed Vosk model session for offline speech recognition."
            )
            SpeechRecognitionRoute.ON_DEVICE -> {
                // Keep the SDK check adjacent to the API-31 invocation. Lint does not
                // propagate @RequiresApi requirements through this policy helper.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    throw IllegalStateException(
                        "The dedicated on-device speech recognizer requires Android 12 or newer."
                    )
                }
            }
            SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT -> SpeechRecognizer.createSpeechRecognizer(context)
            SpeechRecognitionRoute.BLOCKED -> throw IllegalStateException(
                "A guaranteed on-device speech recognizer is unavailable on this device. Enable the explicit online speech fallback in Settings, or install a supported offline speech model when available."
            )
        }
        return recognizer to route
    }

    fun createSystemRecognizer(context: Context): Pair<SpeechRecognizer, SpeechRecognitionRoute> {
        check(allowNetworkFallback(context)) {
            "Online speech recognition is disabled. Enable the explicit online fallback in Voice settings to use this provider."
        }
        check(SpeechRecognizer.isRecognitionAvailable(context)) {
            "No Android speech-recognition provider is installed or enabled."
        }
        return SpeechRecognizer.createSpeechRecognizer(context) to
            SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT
    }

    fun description(route: SpeechRecognitionRoute): String = when (route) {
        SpeechRecognitionRoute.OFFLINE_VOSK_MODEL -> "Speech is processed by the installed Vosk model on this device."
        SpeechRecognitionRoute.ON_DEVICE -> "Speech is processed by Android's dedicated on-device recognizer."
        SpeechRecognitionRoute.SYSTEM_PROVIDER_NETWORK_CONSENT -> "You allowed the Android system speech provider. It may send audio to a remote service."
        SpeechRecognitionRoute.BLOCKED -> "No guaranteed on-device recognizer is available and online speech fallback is disabled."
    }
}
