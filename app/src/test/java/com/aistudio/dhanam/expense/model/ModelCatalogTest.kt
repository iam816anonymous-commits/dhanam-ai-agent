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

    @Test fun qwen03ArtifactRemainsTheSmallDeviceBaseline() {
        val model = ModelCatalog.models.first { it.id == "qwen3-0.6b-int4-nothink-1280" }
        assertTrue(model.downloadUrl.contains("/resolve/6aa2daf/"))
        assertEquals(347_251_840L, model.expectedBytes)
        assertEquals("2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139", model.sha256)
        assertEquals("Apache-2.0", model.license)
        assertEquals(1280, model.contextTokens)
        assertEquals(2L * 1024L * 1024L * 1024L, model.maxProcessPeakBytes)
    }

    @Test fun qwen17bHasItsOwnMemoryTierAndArtifact() {
        val model = ModelCatalog.models.first { it.id == "qwen3-1.7b-int4-4096" }
        assertTrue(model.downloadUrl.contains("/resolve/d9b8a91/"))
        assertEquals(977_184_032L, model.expectedBytes)
        assertEquals("2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", model.sha256)
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
