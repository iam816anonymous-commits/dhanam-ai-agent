package com.aistudio.dhanam.expense.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessMemoryBudgetTest {
    @Test fun readsCurrentResidentMemoryInsteadOfLifetimePeak() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText(
                "Name:\ttest\n" +
                    "VmHWM:\t  2097152 kB\n" +
                    "VmRSS:\t  512000 kB\n"
            )
            // A historical 2 GiB peak must not mask the current 500 MiB resident set.
            assertEquals(512_000L * 1024L, ProcessMemoryBudget.readCurrentResidentBytes(status))
        } finally {
            status.delete()
        }
    }

    @Test fun failsClosedWhenCurrentResidentMemoryCannotBeRead() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("Name:\ttest\nVmHWM:\t  1048576 kB\n")
            assertNull(ProcessMemoryBudget.readCurrentResidentBytes(status))
        } finally {
            status.delete()
        }
    }
}
