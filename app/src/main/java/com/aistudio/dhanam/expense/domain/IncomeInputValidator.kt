package com.aistudio.dhanam.expense.domain

import java.time.LocalDate

data class ValidatedIncome(
    val title: String,
    val amountPaise: Long,
    val category: String,
    val occurredOn: LocalDate,
    val note: String
)

object IncomeInputValidator {
    val categories = listOf("Salary", "Business", "Freelance", "Investment", "Gift", "Other")

    fun validate(title: String, amount: String, category: String, date: String, note: String): ValidatedIncome {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Enter an income description." }
        require(normalizedTitle.length <= 80) { "Income description must be 80 characters or fewer." }
        val amountPaise = MoneyParser.parseRupeesToPaise(amount)
        require(amountPaise > 0L) { "Income amount must be greater than zero." }
        require(category in categories) { "Choose a supported income category." }
        val occurredOn = try {
            LocalDate.parse(date.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid income date in YYYY-MM-DD format.")
        }
        val normalizedNote = note.trim()
        require(normalizedNote.length <= 500) { "Income note must be 500 characters or fewer." }
        return ValidatedIncome(normalizedTitle, amountPaise, category, occurredOn, normalizedNote)
    }
}
