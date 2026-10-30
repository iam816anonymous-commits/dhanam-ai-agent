package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceCsvTransferTest {
    @Test fun quotedCommasQuotesAndNewlinesAndMetadataRoundTrip() {
        val source = listOf(
            ExpenseEntity(title = "Lunch, team", amountPaise = 12345, category = "Food",
                occurredOnEpochDay = LocalDate.parse("2026-10-29").toEpochDay(), note = "said \"hello\"\nsecond line",
                createdAtEpochMillis = 1, updatedAtEpochMillis = 1, paymentMode = "UPI", detectedLanguage = "te")
        )
        val csv = FinanceCsvTransfer.exportCsv(source)
        val parsed = FinanceCsvTransfer.parseCsv(csv, 2)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(1, parsed.expenses.size)
        assertEquals("Lunch, team", parsed.expenses.single().title)
        assertEquals(12345L, parsed.expenses.single().amountPaise)
        assertEquals("said \"hello\"\nsecond line", parsed.expenses.single().note)
        assertEquals("UPI", parsed.expenses.single().paymentMode)
        assertEquals("te", parsed.expenses.single().detectedLanguage)
    }

    @Test fun identicalRowsInOneFileRemainDistinctAndHaveStableImportKeys() {
        val row = "Coffee,3.50,Food,2026-10-01,\n"
        val csv = "description,amount_rupees,category,date,note\n$row$row"
        val first = FinanceCsvTransfer.parseCsv(csv, 2)
        val second = FinanceCsvTransfer.parseCsv(csv, 3)
        assertTrue(first.errors.isEmpty())
        assertEquals(2, first.expenses.size)
        assertNotEquals(first.expenses[0].sourceRecordKey, first.expenses[1].sourceRecordKey)
        assertEquals(first.expenses.map { it.sourceRecordKey }, second.expenses.map { it.sourceRecordKey })
    }

    @Test fun priorFiveColumnDhanamCsvRemainsImportable() {
        val csv = "description,amount_rupees,category,date,note\nCoffee,3.50,Food,2026-10-01,\n"
        val parsed = FinanceCsvTransfer.parseCsv(csv, 2)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(1, parsed.expenses.size)
        assertEquals("Unknown", parsed.expenses.single().paymentMode)
    }

    @Test fun incomeCsvRoundTripsAndUsesStableSourceKeys() {
        val source = listOf(
            IncomeEntity(
                title = "Salary, October",
                amountPaise = 4_250_075L,
                category = "Salary",
                occurredOnEpochDay = LocalDate.parse("2026-10-30").toEpochDay(),
                note = "Payroll \"verified\"\nOctober",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        val csv = FinanceCsvTransfer.exportIncomeCsv(source)
        val first = FinanceCsvTransfer.parseIncomeCsv(csv, 2L)
        val second = FinanceCsvTransfer.parseIncomeCsv(csv, 3L)
        assertTrue(first.errors.isEmpty())
        assertEquals(1, first.income.size)
        assertEquals("Salary, October", first.income.single().title)
        assertEquals(4_250_075L, first.income.single().amountPaise)
        assertEquals("Payroll \"verified\"\nOctober", first.income.single().note)
        assertEquals(first.income.single().sourceRecordKey, second.income.single().sourceRecordKey)
    }

    @Test fun invalidIncomeCsvRowIsReportedWithoutProducingARecord() {
        val csv = "description,amount_rupees,category,date,note\nSalary,0,Salary,2026-10-30,\n"
        val parsed = FinanceCsvTransfer.parseIncomeCsv(csv, 2L)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.income.isEmpty())
    }

    @Test fun invalidRowBlocksWholeImport() {
        val csv = "description,amount_rupees,category,date,note\nCoffee,0,Food,2026-10-01,\n"
        val parsed = FinanceCsvTransfer.parseCsv(csv, 2)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.expenses.isEmpty())
    }
}
