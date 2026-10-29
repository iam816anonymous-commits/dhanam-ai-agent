package com.aistudio.dhanam.expense.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ValidatedExpenseInput(
    val title: String,
    val amountPaise: Long,
    val category: String,
    val occurredOn: LocalDate,
    val note: String
)

object ExpenseInputValidator {
    val categories = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Education", "Other")

    fun validate(title: String, amount: String, category: String, date: String, note: String): ValidatedExpenseInput {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Enter an expense description." }
        require(normalizedTitle.length <= 120) { "Description must be 120 characters or fewer." }
        require(category in categories) { "Choose a valid category." }
        val occurredOn = try {
            LocalDate.parse(date.trim())
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Enter the date as YYYY-MM-DD.")
        }
        val normalizedNote = note.trim()
        require(normalizedNote.length <= 1000) { "Note must be 1,000 characters or fewer." }
        return ValidatedExpenseInput(
            normalizedTitle, MoneyParser.parseRupeesToPaise(amount), category, occurredOn, normalizedNote
        )
    }
}
