package com.aistudio.dhanam.expense.voice

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A short-lived Vosk microphone session. The model is loaded off the main thread and all native
 * objects/audio resources are released on completion, cancellation, timeout, or error.
 */
class OfflineVoskSpeechSession private constructor(
    private val model: Model,
    private val recognizer: Recognizer,
    private val speechService: SpeechService
) {
    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val completed = AtomicBoolean(false)
    private var listener: Listener? = null
    private val timeout = Runnable {
        if (!completed.get()) stop()
    }

    fun start(listener: Listener, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        check(!completed.get()) { "Speech session is already closed." }
        this.listener = listener
        check(speechService.startListening(voskListener)) {
            "The offline speech engine is already recording."
        }
        handler.postDelayed(timeout, timeoutMs)
    }

    /** Stop microphone capture and flush the final partial transcript. */
    fun stop() {
        if (completed.get()) return
        runCatching { speechService.stop() }
        // SpeechService posts its final callback to the main thread after its worker exits.
    }

    /** Cancel without returning any captured transcript. */
    fun cancel() {
        if (!completed.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
        listener = null
        releaseResources(cancel = true)
    }

    private val voskListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            if (completed.get()) return
            val text = jsonText(hypothesis, "partial")
            if (text.isNotBlank()) listener?.onPartial(text)
        }

        override fun onResult(hypothesis: String) {
            val text = jsonText(hypothesis, "text")
            if (text.isNotBlank()) complete(text)
        }

        override fun onFinalResult(hypothesis: String) {
            val text = jsonText(hypothesis, "text")
            if (text.isNotBlank()) complete(text)
            else fail("No speech was recognized. Try again and speak clearly.")
        }

        override fun onError(exception: Exception) {
            fail(exception.message ?: "The offline speech engine could not access the microphone.")
        }

        override fun onTimeout() {
            fail("Offline speech recognition timed out.")
        }
    }

    private fun complete(text: String) {
        if (!completed.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
        try {
            listener?.onFinal(text)
        } finally {
            listener = null
            releaseResources(cancel = false)
        }
    }

    private fun fail(message: String) {
        if (!completed.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
        try {
            listener?.onError(message)
        } finally {
            listener = null
            releaseResources(cancel = false)
        }
    }

    private fun releaseResources(cancel: Boolean) {
        if (cancel) runCatching { speechService.cancel() }
        else runCatching { speechService.stop() }
        runCatching { speechService.shutdown() }
        runCatching { recognizer.close() }
        runCatching { model.close() }
    }

    private fun jsonText(json: String, key: String): String =
        runCatching { JSONObject(json).optString(key).trim() }.getOrDefault("")

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 25_000L

        suspend fun create(modelDirectory: File): OfflineVoskSpeechSession = withContext(Dispatchers.IO) {
            check(OfflineSpeechModelCatalog.isValidModelDirectory(modelDirectory)) {
                "The offline speech model is not installed correctly. Reinstall it from Voice settings."
            }
            val model = try {
                Model(modelDirectory.absolutePath)
            } catch (error: Exception) {
                throw IOException("Could not load the offline speech model: ${error.message ?: "model initialization failed"}", error)
            }
            try {
                val recognizer = Recognizer(model, SAMPLE_RATE)
                try {
                    val service = SpeechService(recognizer, SAMPLE_RATE)
                    OfflineVoskSpeechSession(model, recognizer, service)
                } catch (error: Exception) {
                    recognizer.close()
                    throw error
                }
            } catch (error: Exception) {
                model.close()
                throw error
            }
        }

        private const val SAMPLE_RATE = 16_000.0f
    }
}
