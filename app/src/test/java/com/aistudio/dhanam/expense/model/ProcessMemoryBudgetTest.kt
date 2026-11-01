package com.aistudio.dhanam.expense.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessMemoryBudgetTest {
    @Test fun readsCurrentAndLifetimePeakSeparately() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("VmHWM:\t2097152 kB\nVmRSS:\t512000 kB\nRssAnon:\t400000 kB\nRssFile:\t100000 kB\nRssShmem:\t12000 kB\n")
            val snapshot = ProcessMemoryBudget.readSnapshot(status)
            assertEquals(512_000L * 1024L, snapshot?.residentBytes)
            assertEquals(2_097_152L * 1024L, snapshot?.highWaterBytes)
            assertEquals(400_000L * 1024L, snapshot?.anonymousBytes)
            assertEquals(100_000L * 1024L, snapshot?.fileBackedBytes)
            assertEquals(12_000L * 1024L, snapshot?.sharedBytes)
        } finally { status.delete() }
    }
    @Test fun failsClosedWhenCurrentResidentMemoryCannotBeRead() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("Name:\ttest\nVmHWM:\t1048576 kB\n")
            assertNull(ProcessMemoryBudget.readSnapshot(status))
        } finally { status.delete() }
    }
    @Test fun currentResidentHelperReturnsOnlyCurrentRss() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("VmHWM:\t2048 kB\nVmRSS:\t512 kB\n")
            assertEquals(512L * 1024L, ProcessMemoryBudget.readCurrentResidentBytes(status))
        } finally { status.delete() }
    }
}
