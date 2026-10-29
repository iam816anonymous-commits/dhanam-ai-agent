package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun catalogUsesPinnedNoThinkArtifactAndDigest() {
        val model = ModelCatalog.models.single()
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertTrue(model.downloadUrl.contains("/resolve/6aa2daf/"))
        assertTrue(model.downloadUrl.contains("qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"))
        assertEquals(347_251_840L, model.expectedBytes)
        assertEquals("2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", model.sha256)
        assertEquals("Apache-2.0", model.license)
        assertEquals(1280, model.contextTokens)
        assertEquals(2L * 1024L * 1024L * 1024L, ModelCatalog.MAX_PROCESS_PEAK_BYTES)
    }
}
