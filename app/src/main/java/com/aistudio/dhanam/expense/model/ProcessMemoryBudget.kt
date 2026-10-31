package com.aistudio.dhanam.expense.model

import java.io.File

/**
 * Reads the process's CURRENT resident set size (VmRSS), not VmHWM.
 *
 * VmHWM is a lifetime high-water mark and never resets when memory is released.
 * Using it as a pre-load/current safety gate permanently penalizes an app after
 * an unrelated earlier memory spike. VmRSS is the current resident snapshot.
 */
object ProcessMemoryBudget {
    fun readCurrentResidentBytes(statusFile: File = File("/proc/self/status")): Long? = try {
        statusFile.useLines { lines ->
            val line = lines.firstOrNull { it.startsWith("VmRSS:") } ?: return@useLines null
            val valueKb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                ?: return@useLines null
            valueKb * 1024L
        }
    } catch (_: Exception) {
        null
    }
}
