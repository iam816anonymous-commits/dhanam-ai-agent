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

/** Optional local TTS. The engine exists only while enabled and is shut down with the composition. */
@Composable
fun AgentSpeechFeedback(
    messageId: String?,
    message: String?,
    enabled: Boolean
) {
    val context = LocalContext.current
    var engine by remember(context) { mutableStateOf<TextToSpeech?>(null) }
    var initialized by remember(context) { mutableStateOf(false) }
    var lastSpokenId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            engine = null
            initialized = false
            onDispose { }
        } else {
            var disposed = false
            val instance = TextToSpeech(context.applicationContext) { result ->
                if (!disposed) initialized = result == TextToSpeech.SUCCESS
            }
            engine = instance
            initialized = false
            onDispose {
                disposed = true
                engine = null
                initialized = false
                try { instance.stop() } catch (_: Exception) { }
                try { instance.shutdown() } catch (_: Exception) { }
            }
        }
    }

    LaunchedEffect(messageId, message, enabled, initialized, engine) {
        if (messageId == null || message.isNullOrBlank()) return@LaunchedEffect
        if (!enabled) {
            lastSpokenId = messageId
            return@LaunchedEffect
        }
        val activeEngine = engine
        if (activeEngine == null || !initialized || lastSpokenId == messageId) return@LaunchedEffect
        if (lastSpokenId == null && message.startsWith("Hi, I'm Dhanam.")) {
            lastSpokenId = messageId
            return@LaunchedEffect
        }

        val locale = when {
            message.any { it in '\u0C00'..'\u0C7F' } -> Locale("te", "IN")
            message.any { it in '\u0900'..'\u097F' } -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        if (activeEngine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            lastSpokenId = messageId
            return@LaunchedEffect
        }
        activeEngine.language = locale
        val result = activeEngine.speak(message.take(1200), TextToSpeech.QUEUE_FLUSH, null, messageId)
        if (result == TextToSpeech.SUCCESS) lastSpokenId = messageId
    }
}
