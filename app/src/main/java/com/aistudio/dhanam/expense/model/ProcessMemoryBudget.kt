package com.aistudio.dhanam.expense.model

import java.io.File

/** Reads this app process's Linux VmHWM (peak resident set size) from procfs. */
object ProcessMemoryBudget {
    fun readPeakResidentBytes(statusFile: File = File("/proc/self/status")): Long? = try {
        statusFile.useLines { lines ->
            val line = lines.firstOrNull { it.startsWith("VmHWM:") } ?: return@useLines null
            val valueKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                ?: return@useLines null
            valueKb * 1024L
        }
    } catch (_: Exception) {
        null
    }
}
