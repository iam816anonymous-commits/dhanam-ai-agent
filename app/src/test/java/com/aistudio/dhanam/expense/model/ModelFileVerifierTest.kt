package com.aistudio.dhanam.expense.model

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileVerifierTest {
    @Test fun acceptsOnlyExactLengthAndDigest() {
        val file = File.createTempFile("dhanam-model-test", ".bin")
        try {
            file.writeText("abc")
            assertTrue(ModelFileVerifier.isValid(file, 3L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            assertFalse(ModelFileVerifier.isValid(file, 4L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            assertFalse(ModelFileVerifier.isValid(file, 3L, "00".repeat(32)))
        } finally {
            file.delete()
        }
    }
}
