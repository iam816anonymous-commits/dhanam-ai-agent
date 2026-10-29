package com.aistudio.dhanam.expense.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessMemoryBudgetTest {
    @Test fun parsesPeakResidentMemoryFromProcStatus() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("Name:\ttest\nVmPeak:\t  123456 kB\nVmHWM:\t  1048576 kB\nVmRSS:\t  100000 kB\n")
            assertEquals(1_073_741_824L, ProcessMemoryBudget.readPeakResidentBytes(status))
        } finally {
            status.delete()
        }
    }

    @Test fun failsClosedWhenPeakMemoryCannotBeRead() {
        val status = File.createTempFile("proc-status", ".txt")
        try {
            status.writeText("Name:\ttest\nVmRSS:\t  100000 kB\n")
            assertNull(ProcessMemoryBudget.readPeakResidentBytes(status))
        } finally {
            status.delete()
        }
    }
}
