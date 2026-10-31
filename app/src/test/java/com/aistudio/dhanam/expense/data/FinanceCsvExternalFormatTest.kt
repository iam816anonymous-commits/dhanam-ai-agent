package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCsvExternalFormatTest {
    @Test fun importsCommonGoogleTranslatedOrSpreadsheetHeadersAndIndianDateCurrencyFormats() {
        val csv = "Expense,Amount (INR),Category,Transaction Date,Remarks,Payment Method\n" +
            "Groceries,\"₹1,250.50\",Food,31/10/2025,Monthly supplies,UPI\n" +
            "Bus ticket,Rs. 45.00,Transport,01/11/2025,Office travel,Cash\n"
        val parsed = FinanceCsvTransfer.parseCsv(csv, 10L)
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(2, parsed.expenses.size)
        assertEquals(125_050L, parsed.expenses[0].amountPaise)
        assertEquals("2025-10-31", java.time.LocalDate.ofEpochDay(parsed.expenses[0].occurredOnEpochDay).toString())
        assertEquals("Monthly supplies", parsed.expenses[0].note)
        assertEquals("UPI", parsed.expenses[0].paymentMode)
        assertEquals(4_500L, parsed.expenses[1].amountPaise)
    }

    @Test fun missingCategoryDefaultsToOtherButMissingRequiredColumnsIsRejected() {
        val parsed = FinanceCsvTransfer.parseCsv(
            "Particulars,Cost,Date\nTea,20,2026-10-01\n", 10L
        )
        assertTrue(parsed.errors.isEmpty())
        assertEquals("Other", parsed.expenses.single().category)
        val error = runCatching {
            FinanceCsvTransfer.parseCsv("Description,Category\nTea,Food\n", 10L)
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("description, amount and date"))
    }
}
