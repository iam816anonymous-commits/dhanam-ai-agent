package com.aistudio.dhanam.expense.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Optional local TTS for assistant replies. It never sends text to a remote service.
 * Unsupported installed TTS languages are not silently replaced with another language.
 */
@Composable
fun AgentSpeechFeedback(
    messageId: String?,
    message: String?,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var initialized by remember { mutableStateOf(false) }
    var lastSpokenId by remember { mutableStateOf<String?>(messageId) }
    val engine = remember(context) {
        TextToSpeech(context.applicationContext) { result ->
            initialized = result == TextToSpeech.SUCCESS
        }
    }

    DisposableEffect(engine) {
        onDispose {
            try {
                engine.stop()
                engine.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(messageId, message, enabled, initialized) {
        if (messageId == null || message.isNullOrBlank()) return@LaunchedEffect
        if (!enabled) {
            lastSpokenId = messageId
            return@LaunchedEffect
        }
        if (!initialized || lastSpokenId == messageId) return@LaunchedEffect

        // Speak the script actually returned by the assistant. Never send English text
        // through a Telugu/Hindi voice merely because that is the selected input language.
        val locale = when {
            message.any { it in '\u0C00'..'\u0C7F' } -> Locale("te", "IN")
            message.any { it in '\u0900'..'\u097F' } -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        val availability = engine.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) return@LaunchedEffect

        engine.language = locale
        engine.speak(message.take(1200), TextToSpeech.QUEUE_FLUSH, null, messageId)
        lastSpokenId = messageId
    }
}
