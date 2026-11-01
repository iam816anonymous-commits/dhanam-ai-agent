package com.aistudio.dhanam.expense.voice

import android.content.Context
import android.os.StatFs
import java.io.File

data class OfflineSpeechModelSpec(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val archiveRoot: String,
    val downloadUrl: String,
    val approximateDownloadBytes: Long,
    val accuracyNote: String
)

/** Official Alpha Cephei Vosk model packages; users download only the languages they need. */
object OfflineSpeechModelCatalog {
    private const val MIB = 1024L * 1024L

    val models = listOf(
        OfflineSpeechModelSpec(
            id = "vosk-small-en-in-0.4",
            displayName = "Indian English · Vosk small",
            languageTag = "en-IN",
            archiveRoot = "vosk-model-small-en-in-0.4",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            approximateDownloadBytes = 36L * MIB,
            accuracyNote = "Optimized for Indian English; verify amounts and merchant names."
        ),
        OfflineSpeechModelSpec(
            id = "vosk-small-hi-0.22",
            displayName = "Hindi · Vosk small",
            languageTag = "hi-IN",
            archiveRoot = "vosk-model-small-hi-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            approximateDownloadBytes = 42L * MIB,
            accuracyNote = "Hindi offline recognition; verify financial amounts before submission."
        ),
        OfflineSpeechModelSpec(
            id = "vosk-small-te-0.42",
            displayName = "Telugu · Vosk small",
            languageTag = "te-IN",
            archiveRoot = "vosk-model-small-te-0.42",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
            approximateDownloadBytes = 58L * MIB,
            accuracyNote = "Experimental-quality Telugu small model; recognition accuracy is limited. Review every transcript."
        )
    )

    fun forLanguageTag(languageTag: String): OfflineSpeechModelSpec? {
        val prefix = languageTag.substringBefore('-').lowercase()
        return models.firstOrNull { it.languageTag.substringBefore('-').lowercase() == prefix }
    }

    fun installedDirectory(context: Context, languageTag: String): File? {
        val spec = forLanguageTag(languageTag) ?: return null
        val directory = File(File(context.filesDir, "speech/models"), spec.id)
        return directory.takeIf(::isValidModelDirectory)
    }

    fun isInstalled(context: Context, spec: OfflineSpeechModelSpec): Boolean =
        isValidModelDirectory(File(File(context.filesDir, "speech/models"), spec.id))

    fun availableBytes(context: Context): Long =
        StatFs(context.filesDir.absolutePath).availableBytes

    internal fun isValidModelDirectory(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/mfcc.conf").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/Gr.fst").isFile &&
            File(directory, "graph/HCLr.fst").isFile
}
