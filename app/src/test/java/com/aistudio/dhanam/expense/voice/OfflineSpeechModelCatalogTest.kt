package com.aistudio.dhanam.expense.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSpeechModelCatalogTest {
    @Test
    fun mapsEnglishHindiAndTeluguTagsToDistinctModels() {
        assertEquals("vosk-small-en-us-0.15", OfflineSpeechModelCatalog.forLanguageTag("en-IN")?.id)
        assertEquals("vosk-small-hi-0.22", OfflineSpeechModelCatalog.forLanguageTag("hi-IN")?.id)
        assertEquals(null, OfflineSpeechModelCatalog.forLanguageTag("te-IN"))
    }

    @Test
    fun unsupportedLanguageDoesNotSilentlySelectAnotherModel() {
        assertEquals(null, OfflineSpeechModelCatalog.forLanguageTag("ta-IN"))
    }

    @Test
    fun everyPublishedModelUsesOfficialHttpsArchiveAndHasStorageEstimate() {
        assertTrue(OfflineSpeechModelCatalog.models.isNotEmpty())
        OfflineSpeechModelCatalog.models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://alphacephei.com/vosk/models/"))
            assertTrue(model.approximateDownloadBytes > 0L)
            assertNotNull(model.accuracyNote)
        }
    }
}
