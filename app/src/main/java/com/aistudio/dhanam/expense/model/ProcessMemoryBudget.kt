package com.aistudio.dhanam.expense.model

import java.io.File

data class ProcessMemorySnapshot(
    val residentBytes: Long,
    val highWaterBytes: Long?,
    val anonymousBytes: Long?,
    val fileBackedBytes: Long?,
    val sharedBytes: Long?
)

/** VmRSS is current residency; VmHWM is a lifetime diagnostic, never a current gate. */
object ProcessMemoryBudget {
    fun readSnapshot(statusFile: File = File("/proc/self/status")): ProcessMemorySnapshot? = try {
        statusFile.useLines { lines ->
            val values = lines.mapNotNull { line ->
                val key = line.substringBefore(':')
                if (key !in MEMORY_FIELDS) return@mapNotNull null
                val valueKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                    ?: return@mapNotNull null
                key to valueKb * 1024L
            }.toMap()
            val resident = values["VmRSS"] ?: return@useLines null
            ProcessMemorySnapshot(resident, values["VmHWM"], values["RssAnon"], values["RssFile"], values["RssShmem"])
        }
    } catch (_: Exception) { null }

    fun readCurrentResidentBytes(statusFile: File = File("/proc/self/status")): Long? =
        readSnapshot(statusFile)?.residentBytes

    private val MEMORY_FIELDS = setOf("VmRSS", "VmHWM", "RssAnon", "RssFile", "RssShmem")
}
