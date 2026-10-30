package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceLedgerCsvExporterTest {
    @Test fun exportsIncomeAsSeparateTypedRowsWithExactPaiseAndAlignedColumns() {
        val income = IncomeEntity(
            id = 7,
            title = "Monthly salary",
            amountPaise = 4_250_075L,
            category = "Salary",
            occurredOnEpochDay = LocalDate.parse("2026-10-30").toEpochDay(),
            note = "October payroll",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val csv = FinanceLedgerCsvExporter.export(emptyList(), emptyList(), emptyList(), listOf(income))
        val rows = csv.trim().lines()
        assertTrue(rows.first().startsWith("record_type,id"))
        assertTrue(rows.last().startsWith("INCOME,7"))
        assertTrue(rows.last().contains("Monthly salary,Salary,42500.75,2026-10-30,October payroll"))
        assertTrue(rows.all { it.split(",").size == 23 })
    }

    @Test fun everyRecordTypeMatchesTheExtendedLedgerHeaderWidth() {
        val date = LocalDate.parse("2026-10-30").toEpochDay()
        val loan = KhataLoanEntity(
            id = 1, personName = "Ravi", direction = "LENT", principalPaise = 10_000,
            paidPaise = 0, openedOnEpochDay = date, dueOnEpochDay = null, note = "",
            createdAtEpochMillis = 1, updatedAtEpochMillis = 1
        )
        val repayment = RepaymentEntity(
            id = 1, loanId = 1, amountPaise = 1_000, paidOnEpochDay = date,
            note = "", requestId = "request", createdAtEpochMillis = 1
        )
        val budget = BudgetEntity(
            id = 1, category = "Food", monthEpochDay = date, amountPaise = 50_000,
            createdAtEpochMillis = 1, updatedAtEpochMillis = 1
        )
        val income = IncomeEntity(
            id = 1, title = "Salary", amountPaise = 100_000, category = "Salary",
            occurredOnEpochDay = date, note = "", createdAtEpochMillis = 1, updatedAtEpochMillis = 1
        )
        val rows = FinanceCsvTransfer.parseRows(
            FinanceLedgerCsvExporter.export(listOf(loan), listOf(repayment), listOf(budget), listOf(income))
        )
        assertEquals(5, rows.size)
        rows.forEach { assertEquals(23, it.size) }
    }

    @Test fun exportsLoansRepaymentsAndBudgetsWithTypedRowsAndExactMoney() {
        val loan = KhataLoanEntity(
            id = 1, personName = "Ravi, Kumar", direction = "LENT", principalPaise = 10050, paidPaise = 500,
            openedOnEpochDay = LocalDate.parse("2026-10-01").toEpochDay(), dueOnEpochDay = null,
            note = "=formula", createdAtEpochMillis = 1, updatedAtEpochMillis = 1
        )
        val repayment = RepaymentEntity(
            id = 1, loanId = 1, amountPaise = 500, paidOnEpochDay = LocalDate.parse("2026-10-02").toEpochDay(),
            note = "part payment", requestId = "request-1", createdAtEpochMillis = 2
        )
        val budget = BudgetEntity(
            id = 1, category = "Food", monthEpochDay = LocalDate.parse("2026-10-01").toEpochDay(),
            amountPaise = 250000, createdAtEpochMillis = 1, updatedAtEpochMillis = 1
        )
        val csv = FinanceLedgerCsvExporter.export(listOf(loan), listOf(repayment), listOf(budget))
        assertTrue(csv.contains("LOAN,1,,\"Ravi, Kumar\",LENT,100.50,5.00,95.50"))
        assertTrue(csv.contains("REPAYMENT,1"))
        assertTrue(csv.contains("BUDGET,1"))
        assertTrue(csv.contains("'=formula"))
        assertTrue(csv.contains("2500.00"))
    }
}
