package com.aistudio.dhanam.expense.data

import java.time.LocalDate

/** CSV export for Khata loans, repayment ledger rows and budgets; values remain exact paise-to-rupee decimals. */
object FinanceLedgerCsvExporter {
    fun export(
        loans: List<KhataLoanEntity>,
        repayments: List<RepaymentEntity>,
        budgets: List<BudgetEntity>
    ): String = buildString {
        val header = listOf(
            "record_type", "id", "person", "direction", "principal_rupees", "paid_rupees",
            "outstanding_rupees", "opened_on", "due_on", "phone", "note",
            "repayment_date", "repayment_amount_rupees", "repayment_note",
            "budget_category", "budget_month", "budget_amount_rupees"
        )
        append(header.joinToString(",")).append("\r\n")
        fun money(paise: Long) = "%d.%02d".format(java.util.Locale.ROOT, paise / 100, paise % 100)
        fun row(values: List<String>) { append(values.joinToString(",") { escape(it) }).append("\r\n") }
        loans.forEach { loan ->
            row(listOf(
                "LOAN", loan.id.toString(), loan.personName, loan.direction,
                money(loan.principalPaise), money(loan.paidPaise), money(loan.outstandingPaise),
                LocalDate.ofEpochDay(loan.openedOnEpochDay).toString(),
                loan.dueOnEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
                loan.phoneNumber, loan.note, "", "", "", "", "", ""
            ))
        }
        repayments.forEach { repayment ->
            row(listOf(
                "REPAYMENT", repayment.id.toString(), "", "", "", "", "", "", "", "", "",
                LocalDate.ofEpochDay(repayment.paidOnEpochDay).toString(),
                money(repayment.amountPaise), repayment.note, "", "", ""
            ))
        }
        budgets.forEach { budget ->
            row(listOf(
                "BUDGET", budget.id.toString(), "", "", "", "", "", "", "", "", "", "", "", "",
                budget.category, LocalDate.ofEpochDay(budget.monthEpochDay).toString(), money(budget.amountPaise)
            ))
        }
    }

    private fun escape(value: String): String {
        val safe = if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") ||
            value.startsWith("@") || value.startsWith("\t") || value.startsWith("\r")) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\""
        else safe
    }
}
