package com.aistudio.dhanam.expense.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurity {
    const val PREFS = "dhanam_security"
    private const val SALT_KEY = "pin_salt"
    private const val HASH_KEY = "pin_hash"
    private const val FAILURES_KEY = "pin_failures"
    private const val BLOCKED_UNTIL_KEY = "pin_blocked_until"
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MILLIS = 30_000L

    fun isConfigured(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(HASH_KEY)

    fun setPin(context: Context, pin: String) {
        require(pin.matches(Regex("[0-9]{4,12}"))) { "PIN must contain 4 to 12 digits." }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(FAILURES_KEY, 0).remove(BLOCKED_UNTIL_KEY).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong(BLOCKED_UNTIL_KEY, 0L)) return false
        val saltText = prefs.getString(SALT_KEY, null) ?: return false
        val expectedText = prefs.getString(HASH_KEY, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(expectedText, Base64.NO_WRAP)
        val actual = derive(pin, salt)
        val success = MessageDigest.isEqual(expected, actual)
        if (success) {
            prefs.edit().putInt(FAILURES_KEY, 0).remove(BLOCKED_UNTIL_KEY).apply()
        } else {
            val failures = prefs.getInt(FAILURES_KEY, 0) + 1
            val editor = prefs.edit().putInt(FAILURES_KEY, failures)
            if (failures >= MAX_FAILURES) editor.putLong(BLOCKED_UNTIL_KEY, System.currentTimeMillis() + LOCKOUT_MILLIS)
            editor.apply()
        }
        return success
    }

    fun clearPin(context: Context, currentPin: String): Boolean {
        if (!verifyPin(context, currentPin)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(SALT_KEY).remove(HASH_KEY).remove(FAILURES_KEY).remove(BLOCKED_UNTIL_KEY).apply()
        return true
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
