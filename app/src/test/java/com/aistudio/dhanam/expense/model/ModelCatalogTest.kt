package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun everyDeviceTierOffersTwoDistinctModels() {
        assertEquals(6, ModelCatalog.models.size)
        ModelTier.values().forEach { tier ->
            assertEquals("Each tier must offer two models: ${tier.title}", 2, ModelCatalog.modelsFor(tier).size)
            assertEquals(2, ModelCatalog.modelsFor(tier).map { it.id }.distinct().size)
        }
    }

    @Test fun everyCatalogEntryHasPinnedVerifiedDownloadMetadata() {
        ModelCatalog.models.forEach { model ->
            assertTrue("${model.id} must use HTTPS", model.downloadUrl.startsWith("https://"))
            assertTrue("${model.id} must pin a revision", model.downloadUrl.contains("/resolve/"))
            assertTrue("${model.id} must use a LiteRT-LM bundle", model.fileName.endsWith(".litertlm"))
            assertEquals(64, model.sha256.length)
            assertTrue(model.expectedBytes > 0L)
            assertTrue(model.minTotalRamBytes > 0L)
            assertTrue(model.minAvailableRamBytes > 0L)
            assertTrue(model.minFreeStorageBytes > model.expectedBytes)
            assertTrue(model.maxProcessPeakBytes >= model.expectedBytes)
        }
    }

    @Test fun compactModelsHaveLowerRequirementsThanAdvancedModels() {
        val compact = ModelCatalog.modelsFor(ModelTier.COMPACT)
        val advanced = ModelCatalog.modelsFor(ModelTier.ADVANCED)
        assertTrue(compact.maxOf { it.minTotalRamBytes } < advanced.minOf { it.minTotalRamBytes })
        assertTrue(compact.maxOf { it.minAvailableRamBytes } < advanced.minOf { it.minAvailableRamBytes })
        assertTrue(compact.maxOf { it.maxProcessPeakBytes } < advanced.minOf { it.maxProcessPeakBytes })
    }

    @Test fun smollmArtifactUsesPublishedExactSizeAndDigest() {
        val model = ModelCatalog.models.first { it.id == "smollm2-360m-instruct" }
        assertTrue(model.downloadUrl.contains("/resolve/507c99cfe6541ba2bcd84818786f7b025935e5e1/"))
        assertEquals(373_719_040L, model.expectedBytes)
        assertEquals("8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e", model.sha256)
        assertEquals("Apache-2.0", model.license)
    }

    @Test fun qwen25ArtifactUsesPublishedExactSizeAndDigest() {
        val model = ModelCatalog.models.first { it.id == "qwen25-1.5b-instruct-q8-4096" }
        assertTrue(model.downloadUrl.contains("/resolve/4526a9ff32b96063c7b07f0d681af7dc6c8ec342/"))
        assertEquals(1_598_603_264L, model.expectedBytes)
        assertEquals("2ac49902133f729199bbd23ad6714df32b20585c786f2546d2293cc371f35278", model.sha256)
        assertEquals("Apache-2.0", model.license)
    }

    @Test fun qwen03ArtifactRemainsTheSmallDeviceBaseline() {
        val model = ModelCatalog.models.first { it.id == "qwen3-0.6b-int4-nothink-1280" }
        assertTrue(model.downloadUrl.contains("/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/"))
        assertEquals(347_251_840L, model.expectedBytes)
        assertEquals("2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", model.sha256)
        assertEquals("Apache-2.0", model.license)
        assertEquals(1280, model.contextTokens)
        assertEquals(2L * 1024L * 1024L * 1024L, model.maxProcessPeakBytes)
    }

    @Test fun qwen17bHasItsOwnMemoryTierAndArtifact() {
        val model = ModelCatalog.models.first { it.id == "qwen3-1.7b-int4-4096" }
        assertTrue(model.downloadUrl.contains("/resolve/87603f0ff3fe74b9b77ed0477edb773b4b596f32/"))
        assertEquals(977_184_032L, model.expectedBytes)
        assertEquals("faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9", model.sha256)
        assertEquals(4096, model.contextTokens)
        assertEquals(8L * 1024L * 1024L * 1024L, model.minTotalRamBytes)
        assertEquals(4L * 1024L * 1024L * 1024L, model.minAvailableRamBytes)
    }

    @Test fun qwen4bIsRestrictedToHighMemoryDevices() {
        val model = ModelCatalog.models.first { it.id == "qwen3-4b-mixed-int4-2048" }
        assertTrue(model.downloadUrl.contains("/resolve/2f7415b4e3a149c16b929659d146a6eaf6d99a34/"))
        assertEquals(2_659_053_760L, model.expectedBytes)
        assertEquals("569fe029f51e55b6377f98ebd7f6d26431bc73e1d632034f5389f2fd720da701", model.sha256)
        assertEquals(16L * 1024L * 1024L * 1024L, model.minTotalRamBytes)
        assertEquals(8L * 1024L * 1024L * 1024L, model.minAvailableRamBytes)
        assertEquals(6L * 1024L * 1024L * 1024L, model.maxProcessPeakBytes)
    }

    @Test fun phiReasoningBundleUsesCurrentPinnedRevisionAndDigest() {
        val model = ModelCatalog.models.first { it.id == "phi4-mini-reasoning-int4-4096" }
        assertTrue(model.downloadUrl.contains("/resolve/62a183af7b3d3d46f8df17dde9bf8381988bbf68/"))
        assertEquals(2_784_187_376L, model.expectedBytes)
        assertEquals("d507262ca3d01d93d59c573a82e6f2eeb3aec21d9a67c31fb4c0f93d17225b8f", model.sha256)
        assertEquals("MIT", model.license)
        assertEquals(4096, model.contextTokens)
    }
}
