package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceLedgerCsvExporterTest {
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
