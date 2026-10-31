package com.aistudio.dhanam.expense.voice

import android.content.Context
import ai.picovoice.porcupine.Porcupine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Creates the custom English "Hey Jarvis" Porcupine keyword model on explicit user request. */
object WakeWordModelInstaller {
    const val PHRASE = "Hey Jarvis"

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "wake-word"), "hey_jarvis_android.ppn")

    suspend fun prepare(context: Context, accessKey: String): File = withContext(Dispatchers.IO) {
        require(accessKey.isNotBlank()) { "A Picovoice AccessKey is required to create the Hey Jarvis model." }
        val output = modelFile(context)
        output.parentFile?.mkdirs()
        output.delete()
        Porcupine.trainWakeWordFromPhrase(accessKey.trim(), output.absolutePath, "en", PHRASE)
        check(output.isFile && output.length() > 0L) {
            "Wake-word training did not produce a valid model file. Check the AccessKey and internet connection."
        }
        output
    }
}
