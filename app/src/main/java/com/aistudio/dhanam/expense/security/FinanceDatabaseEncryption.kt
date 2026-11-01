package com.aistudio.dhanam.expense.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Creates and protects the SQLCipher database key.
 *
 * Existing plaintext Room databases are exported to a temporary encrypted database and verified
 * before the original file is replaced. The plaintext source is retained until the encrypted
 * replacement has been reopened successfully. A missing/lost Keystore key is a hard error:
 * this class never deletes or recreates an unreadable finance database.
 */
object FinanceDatabaseEncryption {
    private const val PREFS = "dhanam_database_security"
    private const val CIPHER_TEXT_KEY = "database_key_ciphertext"
    private const val IV_KEY = "database_key_iv"
    private const val MASTER_KEY_ALIAS = "dhanam_finance_database_master_v1"
    private const val KEY_SIZE_BYTES = 32

    fun prepare(context: Context, databaseName: String): ByteArray {
        System.loadLibrary("sqlcipher")
        val database = context.getDatabasePath(databaseName)
        database.parentFile?.mkdirs()
        val backup = File(database.absolutePath + ".plaintext-backup")
        val temporary = File(database.absolutePath + ".encrypted.tmp")

        // Recover safely if the process was killed between the two atomic renames.
        if (!database.exists() && backup.exists()) {
            check(backup.renameTo(database)) {
                "Could not recover the existing finance database. Your original data is still in the app's database folder."
            }
        }

        val passphrase = readOrCreatePassphrase(context, database)
        if (backup.exists()) {
            if (database.exists() && isReadableWithKey(database, passphrase)) {
                backup.delete()
                temporary.delete()
                return passphrase
            }
            // A previous process may have stopped after staging the plaintext original but
            // before successfully installing the encrypted replacement.
            if (database.exists()) deleteDatabaseFiles(database)
            check(backup.renameTo(database)) {
                "Could not restore the staged original finance database. The backup has been preserved."
            }
        }
        if (!database.exists() || database.length() == 0L) return passphrase

        if (isReadableWithKey(database, passphrase)) {
            temporary.delete()
            return passphrase
        }

        // If the key does not open the file, only a valid plaintext SQLite database may be
        // migrated. An encrypted/corrupt file is never overwritten as a recovery shortcut.
        temporary.delete()
        exportPlaintextToEncrypted(database, temporary, passphrase)
        check(isReadableWithKey(temporary, passphrase)) {
            "The encrypted finance database failed verification. The original database has not been replaced."
        }

        if (backup.exists()) backup.delete()
        check(database.renameTo(backup)) {
            "Could not safely stage the original finance database for encryption."
        }
        if (!temporary.renameTo(database)) {
            backup.renameTo(database)
            throw IllegalStateException("Could not install the encrypted finance database. The original database was restored.")
        }

        // The export has been verified and the encrypted file is now in place.
        deleteSidecars(database)
        check(isReadableWithKey(database, passphrase)) {
            "The installed encrypted finance database could not be reopened. The original plaintext backup has been preserved."
        }
        backup.delete()
        return passphrase
    }

    fun createRoomFactory(passphrase: ByteArray): SupportOpenHelperFactory =
        SupportOpenHelperFactory(passphrase)

    private fun deleteSidecars(database: File) {
        File(database.absolutePath + "-wal").delete()
        File(database.absolutePath + "-shm").delete()
        File(database.absolutePath + "-journal").delete()
    }

    private fun deleteDatabaseFiles(database: File) {
        database.delete()
        deleteSidecars(database)
    }

    private fun readOrCreatePassphrase(context: Context, database: File): ByteArray {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedCiphertext = preferences.getString(CIPHER_TEXT_KEY, null)
        val encodedIv = preferences.getString(IV_KEY, null)
        if (!encodedCiphertext.isNullOrBlank() && !encodedIv.isNullOrBlank()) {
            return decryptKey(encodedCiphertext, encodedIv)
        }
        if (database.exists() && database.length() > 0L) {
            check(isReadableAsPlaintext(database)) {
                "The finance database exists but its encryption key is missing or unavailable. Dhanam has stopped to protect your data; do not uninstall the app or clear its storage."
            }
        }

        val rawKey = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val encodedKey = Base64.encodeToString(rawKey, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val ciphertext = Base64.encodeToString(cipher.doFinal(encodedKey.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        check(preferences.edit().putString(CIPHER_TEXT_KEY, ciphertext).putString(IV_KEY, iv).commit()) {
            "Could not persist the finance database encryption key."
        }
        rawKey.fill(0)
        return encodedKey.toByteArray(Charsets.UTF_8)
    }

    private fun decryptKey(ciphertext: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        return String(
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
            Charsets.UTF_8
        ).toByteArray(Charsets.UTF_8)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun isReadableAsPlaintext(file: File): Boolean = runCatching {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            byteArrayOf(),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        )
        try {
            database.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()).use { cursor ->
                cursor.moveToFirst() && cursor.getLong(0) >= 0L
            }
        } finally {
            database.close()
        }
    }.getOrDefault(false)

    private fun isReadableWithKey(file: File, passphrase: ByteArray): Boolean = runCatching {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        )
        try {
            database.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()).use { cursor ->
                cursor.moveToFirst() && cursor.getLong(0) >= 0L
            }
        } finally {
            database.close()
        }
    }.getOrDefault(false)

    private fun exportPlaintextToEncrypted(source: File, destination: File, passphrase: ByteArray) {
        val database = try {
            SQLiteDatabase.openDatabase(
                source.absolutePath,
                byteArrayOf(),
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )
        } catch (error: Exception) {
            throw IllegalStateException(
                "The existing finance database is neither readable with its saved key nor a valid plaintext database. It was left untouched.",
                error
            )
        }
        try {
            // Force a schema read before export so an encrypted file with a missing key cannot
            // be mistaken for a plaintext database.
            database.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()).use { it.moveToFirst() }
            database.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(destination.absolutePath, String(passphrase, Charsets.UTF_8))
            )
            database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            database.execSQL("DETACH DATABASE encrypted")
        } catch (error: Exception) {
            destination.delete()
            throw IllegalStateException("Could not encrypt the existing finance database. Original data was left untouched.", error)
        } finally {
            database.close()
        }
    }
}
