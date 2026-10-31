package com.aistudio.dhanam.expense.ui.components

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VoiceTranslationResult(
    val originalText: String,
    val normalizedText: String,
    val sourceLanguage: String,
    val wasTranslated: Boolean
)

/**
 * On-device speech-text normalization for Dhanam.
 * Telugu/Hindi translation models must be explicitly downloaded from Settings first.
 * No network translation or silent model download is performed by this class.
 */
object VoiceTranslationEngine {
    suspend fun translateToEnglish(
        context: Context,
        text: String,
        preferredLanguageTag: String
    ): VoiceTranslationResult = withContext(Dispatchers.IO) {
        val original = text.trim()
        require(original.isNotEmpty()) { "There is no transcript to translate." }

        val identifier = LanguageIdentification.getClient()
        val detected = try {
            Tasks.await(identifier.identifyLanguage(original))
        } finally {
            identifier.close()
        }
        val language = if (detected == "und") {
            preferredLanguageTag.substringBefore('-').lowercase()
        } else {
            detected.substringBefore('-').lowercase()
        }

        if (language == "en") {
            return@withContext VoiceTranslationResult(original, original, "English", false)
        }
        if (language != "te" && language != "hi") {
            throw IllegalStateException(
                "Dhanam currently translates English, Telugu and Hindi voice input. Detected language: $language. Select Telugu or Hindi recognition and try again."
            )
        }

        val sourceLanguage = TranslateLanguage.fromLanguageTag(language)
            ?: throw IllegalStateException("The on-device translator does not support language '$language'.")
        val targetLanguage = TranslateLanguage.ENGLISH
        val manager = RemoteModelManager.getInstance()
        val downloaded = Tasks.await(manager.getDownloadedModels(TranslateRemoteModel::class.java))
        if (downloaded.none { it.language == sourceLanguage }) {
            val name = if (language == "te") "Telugu" else "Hindi"
            throw IllegalStateException(
                "$name translation model is not installed. Open Settings → Voice and language → Translation models and download it over Wi-Fi. Your transcript is preserved and nothing was sent."
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
        )
        try {
            val translated = Tasks.await(translator.translate(original)).trim()
            if (translated.isBlank()) {
                throw IllegalStateException("Translation returned empty text. Review the transcript and try again.")
            }
            VoiceTranslationResult(
                originalText = original,
                normalizedText = translated,
                sourceLanguage = if (language == "te") "Telugu" else "Hindi",
                wasTranslated = true
            )
        } finally {
            translator.close()
        }
    }
}
