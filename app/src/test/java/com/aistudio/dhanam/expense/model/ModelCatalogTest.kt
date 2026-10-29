package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun catalogHasMultipleModels() { assertEquals(3, ModelCatalog.models.size) }
    @Test fun catalogUsesPinnedNoThinkArtifactAndDigest() {
        val model = ModelCatalog.models.first { it.id == "qwen3-0.6b-int4-nothink-1280" }
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertTrue(model.downloadUrl.contains("/resolve/6aa2daf/"))
        assertTrue(model.downloadUrl.contains("qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"))
        assertEquals(347_251_840L, model.expectedBytes)
        assertEquals("2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", model.sha256)
        assertEquals("Apache-2.0", model.license)
        assertEquals(1280, model.contextTokens)
        assertEquals(2L * 1024L * 1024L * 1024L, model.maxProcessPeakBytes)

        val larger = ModelCatalog.models.first { it.id == "qwen3-1.7b-int4-4096" }
        assertTrue(larger.downloadUrl.startsWith("https://"))
        assertTrue(larger.downloadUrl.contains("/resolve/d9b8a91/"))
        assertEquals(977_184_032L, larger.expectedBytes)
        assertEquals("2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", larger.sha256)
        assertEquals("Apache-2.0", larger.license)
        assertEquals(4096, larger.contextTokens)
        assertEquals(8L * 1024L * 1024L * 1024L, larger.minTotalRamBytes)
        assertEquals(4L * 1024L * 1024L * 1024L, larger.minAvailableRamBytes)
        assertTrue(larger.maxProcessPeakBytes > model.maxProcessPeakBytes)

        val highMemory = ModelCatalog.models.first { it.id == "qwen3-4b-mixed-int4-2048" }
        assertTrue(highMemory.downloadUrl.startsWith("https://"))
        assertTrue(highMemory.downloadUrl.contains("/resolve/2f7415b4e3a149c16b929659d146a6eaf6d99a34/"))
        assertEquals(2_659_053_760L, highMemory.expectedBytes)
        assertEquals("569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701", highMemory.sha256)
        assertEquals("Apache-2.0", highMemory.license)
        assertEquals(2048, highMemory.contextTokens)
        assertEquals(12L * 1024L * 1024L * 1024L, highMemory.minTotalRamBytes)
        assertEquals(7L * 1024L * 1024L * 1024L, highMemory.minAvailableRamBytes)
        assertEquals(6L * 1024L * 1024L * 1024L, highMemory.maxProcessPeakBytes)
    }
}
