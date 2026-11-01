package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResourceProfilerTest {
    private val mib = 1024L * 1024L
    private fun model() = ModelSpec(
        id = "test", displayName = "Test", tier = ModelTier.COMPACT, fileName = "test.litertlm",
        downloadUrl = "https://example.com/model", expectedBytes = 10L, sha256 = "0".repeat(64),
        license = "test", contextTokens = 128, minTotalRamBytes = 2L * 1024L * mib,
        minAvailableRamBytes = 1024L * mib, minFreeStorageBytes = 100L,
        runtimeRequirement = "test", sourceUrl = "https://example.com",
        maxProcessPeakBytes = 2L * 1024L * mib
    )
    private fun profile(available: Long = 3L * 1024L * mib, threshold: Long = 512L * mib, low: Boolean = false) =
        DeviceMemoryProfile(4L * 1024L * mib, available, low, threshold, null, 0L, 1L)

    @Test fun runtimeAdmissionPreservesAndroidSystemReserve() {
        assertEquals(1536L * mib, ModelResourceProfiler.requiredAvailableRamBytes(model(), profile()))
        assertTrue(ModelResourceProfiler.runtimeBlockReasons(model(), profile(available = 2L * 1024L * mib), 1024L * mib).isEmpty())
    }
    @Test fun blocksBelowModelRequirementPlusSystemReserve() {
        val reasons = ModelResourceProfiler.runtimeBlockReasons(model(), profile(available = 1400L * mib), 1024L * mib)
        assertTrue(reasons.any { it.contains("safety reserve") })
    }
    @Test fun blocksWhenAndroidReportsLowMemory() {
        val reasons = ModelResourceProfiler.runtimeBlockReasons(model(), profile(low = true), 1024L * mib)
        assertTrue(reasons.any { it.contains("low memory") })
    }
    @Test fun storageCheckIsIndependent() {
        val reasons = ModelResourceProfiler.runtimeBlockReasons(model(), profile(), 50L)
        assertTrue(reasons.any { it.contains("storage") })
    }
}
