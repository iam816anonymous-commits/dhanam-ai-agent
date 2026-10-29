package com.aistudio.dhanam.expense.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinHashingTest {
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    @Test fun samePinAndSaltMatchButDifferentSaltDoesNot() {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val otherSalt = ByteArray(16).also(SecureRandom()::nextBytes)
        val expected = derive("123456", salt)
        assertTrue(MessageDigest.isEqual(expected, derive("123456", salt)))
        assertFalse(MessageDigest.isEqual(expected, derive("123457", salt)))
        assertFalse(MessageDigest.isEqual(expected, derive("123456", otherSalt)))
    }
}
