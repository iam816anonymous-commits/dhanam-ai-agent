package com.aistudio.dhanam.expense.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurity {
    const val PREFS = "dhanam_security"
    private const val LEGACY_SECURE_PREFS = "dhanam_secure_pin"
    private const val LEGACY_PREFS = "dhanam_prefs"
    private const val SALT_KEY = "pin_salt"
    private const val HASH_KEY = "pin_hash"
    private const val FAILURES_KEY = "pin_failures"
    private const val BLOCKED_UNTIL_KEY = "pin_blocked_until"
    private const val BIOMETRIC_ENABLED_KEY = "biometric_unlock_enabled"
    private const val LEGACY_PIN_KEY = "app_pin"
    private const val LEGACY_SALT_KEY = "app_pin_salt"
    private const val ITERATIONS = 150_000
    private const val LEGACY_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MILLIS = 30_000L

    fun isConfigured(context: Context): Boolean {
        val current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldSecure = context.getSharedPreferences(LEGACY_SECURE_PREFS, Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        return current.contains(HASH_KEY) || oldSecure.contains(LEGACY_PIN_KEY) || oldPrefs.contains(LEGACY_PIN_KEY)
    }

    fun setPin(context: Context, pin: String) {
        require(pin.matches(Regex("[0-9]{4,12}"))) { "PIN must contain 4 to 12 digits." }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt, ITERATIONS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(FAILURES_KEY, 0).remove(BLOCKED_UNTIL_KEY).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong(BLOCKED_UNTIL_KEY, 0L)) return false
        val saltText = prefs.getString(SALT_KEY, null)
        val expectedText = prefs.getString(HASH_KEY, null)
        if (saltText != null && expectedText != null) {
            val expected = Base64.decode(expectedText, Base64.NO_WRAP)
            val actual = derive(pin, Base64.decode(saltText, Base64.NO_WRAP), ITERATIONS)
            val success = MessageDigest.isEqual(expected, actual)
            if (success) clearFailureCount(prefs) else recordFailure(prefs)
            return success
        }

        // Compatibility path for the previous app. A valid legacy PIN is immediately re-hashed
        // using the current KDF, then removed from the legacy preference files.
        if (verifyLegacyPin(context, pin)) {
            setPin(context, pin)
            context.getSharedPreferences(LEGACY_SECURE_PREFS, Context.MODE_PRIVATE).edit()
                .remove(LEGACY_PIN_KEY).remove(LEGACY_SALT_KEY).remove("pin_failed_attempts").remove("pin_lockout_until").apply()
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().remove(LEGACY_PIN_KEY).apply()
            return true
        }
        recordFailure(prefs)
        return false
    }

    fun isBiometricEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, enabled && isConfigured(context)).apply()
    }

    fun clearPin(context: Context, currentPin: String): Boolean {
        if (!verifyPin(context, currentPin)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(SALT_KEY).remove(HASH_KEY).remove(FAILURES_KEY).remove(BLOCKED_UNTIL_KEY).remove(BIOMETRIC_ENABLED_KEY).apply()
        context.getSharedPreferences(LEGACY_SECURE_PREFS, Context.MODE_PRIVATE).edit()
            .remove(LEGACY_PIN_KEY).remove(LEGACY_SALT_KEY).apply()
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().remove(LEGACY_PIN_KEY).apply()
        return true
    }

    private fun verifyLegacyPin(context: Context, pin: String): Boolean {
        val secure = context.getSharedPreferences(LEGACY_SECURE_PREFS, Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val stored = secure.getString(LEGACY_PIN_KEY, null) ?: oldPrefs.getString(LEGACY_PIN_KEY, null) ?: return false
        val saltHex = secure.getString(LEGACY_SALT_KEY, null)
        if (!saltHex.isNullOrBlank() && saltHex.length % 2 == 0) {
            val salt = runCatching { hexToBytes(saltHex) }.getOrNull() ?: return false
            val currentHash = bytesToHex(derive(pin, salt, LEGACY_ITERATIONS))
            if (MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), currentHash.toByteArray(Charsets.UTF_8))) return true
        }
        val legacySha = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(("Dhanam_Security_Salt_v2#" + pin).toByteArray(Charsets.UTF_8))
            bytesToHex(digest)
        }.getOrDefault("")
        return MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), pin.toByteArray(Charsets.UTF_8)) ||
            (legacySha.isNotEmpty() && MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), legacySha.toByteArray(Charsets.UTF_8)))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun clearFailureCount(prefs: android.content.SharedPreferences) {
        prefs.edit().putInt(FAILURES_KEY, 0).remove(BLOCKED_UNTIL_KEY).apply()
    }

    private fun recordFailure(prefs: android.content.SharedPreferences) {
        val failures = prefs.getInt(FAILURES_KEY, 0) + 1
        val editor = prefs.edit().putInt(FAILURES_KEY, failures)
        if (failures >= MAX_FAILURES) editor.putLong(BLOCKED_UNTIL_KEY, System.currentTimeMillis() + LOCKOUT_MILLIS)
        editor.apply()
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index ->
        ((Character.digit(hex[index * 2], 16) shl 4) + Character.digit(hex[index * 2 + 1], 16)).toByte()
    }
}
