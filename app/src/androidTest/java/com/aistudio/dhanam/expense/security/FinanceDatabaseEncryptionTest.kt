package com.aistudio.dhanam.expense.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceDatabaseEncryptionTest {
    @Test
    fun plaintextRoomDatabaseIsExportedAndVerifiedBeforeReplacement() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase_a_${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        try {
            val plaintext = PlainSQLiteDatabase.openOrCreateDatabase(file, null)
            plaintext.execSQL("CREATE TABLE security_probe (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            plaintext.execSQL("INSERT INTO security_probe(value) VALUES ('ledger-preserved')")
            plaintext.execSQL("PRAGMA user_version = 11")
            plaintext.execSQL("PRAGMA application_id = 12345")
            plaintext.close()

            val passphrase = FinanceDatabaseEncryption.prepare(context, name)
            val encrypted = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null
            )
            try {
                encrypted.rawQuery("SELECT value FROM security_probe WHERE id = 1", emptyArray()).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("ledger-preserved", cursor.getString(0))
                }
                encrypted.rawQuery("PRAGMA user_version", emptyArray()).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(11, cursor.getInt(0))
                }
                encrypted.rawQuery("PRAGMA application_id", emptyArray()).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(12345, cursor.getInt(0))
                }
            } finally {
                encrypted.close()
            }

            val header = ByteArray(16)
            FileInputStream(file).use { input -> assertEquals(16, input.read(header)) }
            assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))

            // Opening the already-encrypted file again must reuse the same Keystore-wrapped key.
            assertArrayEquals(passphrase, FinanceDatabaseEncryption.prepare(context, name))
        } finally {
            context.deleteDatabase(name)
            File(file.absolutePath + ".plaintext-backup").delete()
            File(file.absolutePath + ".encrypted.tmp").delete()
        }
    }
}
