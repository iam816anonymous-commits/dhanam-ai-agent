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
            id = "vosk-small-en-us-0.15",
            displayName = "English · General",
            languageTag = "en-US",
            archiveRoot = "vosk-model-small-en-us-0.15",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            approximateDownloadBytes = 40L * MIB,
            accuracyNote = "Published WER 9.85 on LibriSpeech test-clean and 10.38 on TEDLIUM; accent mismatch can affect results."
        ),
        OfflineSpeechModelSpec(
            id = "vosk-small-en-in-0.4",
            displayName = "English · Indian accent",
            languageTag = "en-IN",
            archiveRoot = "vosk-model-small-en-in-0.4",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            approximateDownloadBytes = 36L * MIB,
            accuracyNote = "Published WER 49.05 on NPTEL Pure. Experimental for financial commands; verify every amount."
        ),
        OfflineSpeechModelSpec(
            id = "vosk-small-hi-0.22",
            displayName = "Hindi",
            languageTag = "hi-IN",
            archiveRoot = "vosk-model-small-hi-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            approximateDownloadBytes = 42L * MIB,
            accuracyNote = "Published WER 20.89 on IITM Challenge and 24.72 on MUCS Challenge; verify amounts."
        )
    )

    fun forLanguageTag(languageTag: String): OfflineSpeechModelSpec? {
        val prefix = languageTag.substringBefore('-').lowercase()
        return models.firstOrNull { it.languageTag.substringBefore('-').lowercase() == prefix }
    }

    fun installedDirectory(context: Context, languageTag: String): File? {
        val prefix = languageTag.substringBefore('-').lowercase()
        val candidates = models.filter { it.languageTag.substringBefore('-').lowercase() == prefix }
        if (candidates.isEmpty()) return null
        val preferences = context.getSharedPreferences("dhanam_voice", Context.MODE_PRIVATE)
        val selectedId = preferences.getString("offline_speech_model_$prefix", null)
        val ordered = candidates.sortedByDescending { it.id == selectedId }
        return ordered.firstNotNullOfOrNull { spec ->
            File(File(context.filesDir, "speech/models"), spec.id)
                .takeIf(::isValidModelDirectory)
        }
    }

    fun selectedModelId(context: Context, languageTag: String): String? {
        val prefix = languageTag.substringBefore('-').lowercase()
        val directory = installedDirectory(context, languageTag) ?: return null
        return models.firstOrNull { it.id == directory.name }?.id
    }

    fun selectModel(context: Context, spec: OfflineSpeechModelSpec) {
        require(isInstalled(context, spec)) { "Install this speech model before selecting it." }
        val prefix = spec.languageTag.substringBefore('-').lowercase()
        context.getSharedPreferences("dhanam_voice", Context.MODE_PRIVATE)
            .edit()
            .putString("offline_speech_model_$prefix", spec.id)
            .apply()
    }

    fun isInstalled(context: Context, spec: OfflineSpeechModelSpec): Boolean =
        isValidModelDirectory(File(File(context.filesDir, "speech/models"), spec.id))

    fun availableBytes(context: Context): Long =
        StatFs(context.filesDir.absolutePath).availableBytes

    internal fun isValidModelDirectory(directory: File): Boolean {
        val splitGraph = File(directory, "graph/HCLr.fst").isFile &&
            File(directory, "graph/Gr.fst").isFile
        val combinedGraph = File(directory, "graph/HCLG.fst").isFile
        return File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/mfcc.conf").isFile &&
            File(directory, "conf/model.conf").isFile &&
            (splitGraph || combinedGraph)
    }
}
