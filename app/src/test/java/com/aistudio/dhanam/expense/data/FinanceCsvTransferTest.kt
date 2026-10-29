package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceCsvTransferTest {
    @Test fun quotedCommasQuotesAndNewlinesRoundTrip() {
        val source = listOf(
            ExpenseEntity(title = "Lunch, team", amountPaise = 12345, category = "Food",
                occurredOnEpochDay = LocalDate.parse("2026-10-29").toEpochDay(), note = "said \"hello\"\nsecond line",
                createdAtEpochMillis = 1, updatedAtEpochMillis = 1)
        )
        val csv = FinanceCsvTransfer.exportCsv(source)
        val parsed = FinanceCsvTransfer.parseCsv(csv, 2)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(1, parsed.expenses.size)
        assertEquals("Lunch, team", parsed.expenses.single().title)
        assertEquals(12345L, parsed.expenses.single().amountPaise)
        assertEquals("said \"hello\"\nsecond line", parsed.expenses.single().note)
    }

    @Test fun invalidRowBlocksWholeImport() {
        val csv = "description,amount_rupees,category,date,note\nCoffee,0,Food,2026-10-01,\n"
        val parsed = FinanceCsvTransfer.parseCsv(csv, 2)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.expenses.isEmpty())
    }
}
