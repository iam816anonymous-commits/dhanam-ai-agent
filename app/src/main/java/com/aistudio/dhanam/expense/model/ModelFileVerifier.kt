package com.aistudio.dhanam.expense.model

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ModelFileVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isValid(file: File, expectedBytes: Long, expectedSha256: String): Boolean =
        file.isFile && file.length() == expectedBytes &&
            sha256(file).equals(expectedSha256, ignoreCase = true)
}
