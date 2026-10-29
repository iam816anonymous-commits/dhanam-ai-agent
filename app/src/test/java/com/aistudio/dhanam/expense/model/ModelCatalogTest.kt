package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun catalogUsesPinnedHttpsArtifactAndDigest() {
        val model = ModelCatalog.models.single()
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertTrue(model.downloadUrl.contains("154c5de0fc0fc5002a7da570ba8c67edffa32ca2"))
        assertEquals(497_516_544L, model.expectedBytes)
        assertEquals("7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1", model.sha256)
        assertEquals("Apache-2.0", model.license)
    }
}
