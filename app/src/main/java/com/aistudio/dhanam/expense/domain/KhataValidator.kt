package com.aistudio.dhanam.expense.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ValidatedKhataInput(
    val personName: String,
    val direction: String,
    val principalPaise: Long,
    val openedOn: LocalDate,
    val dueOn: LocalDate?,
    val note: String
)

object KhataValidator {
    fun validate(person: String, direction: String, amount: String, openedOn: String, dueOn: String, note: String): ValidatedKhataInput {
        val normalizedPerson = person.trim()
        require(normalizedPerson.isNotEmpty()) { "Enter the person's name." }
        require(normalizedPerson.length <= 100) { "Name must be 100 characters or fewer." }
        require(direction == LENT || direction == BORROWED) { "Choose Lent or Borrowed." }
        val opened = parseDate(openedOn, "Enter the start date as YYYY-MM-DD.")
        val due = dueOn.trim().takeIf { it.isNotEmpty() }?.let { parseDate(it, "Enter the due date as YYYY-MM-DD.") }
        require(due == null || !due.isBefore(opened)) { "Due date cannot be before the start date." }
        val normalizedNote = note.trim()
        require(normalizedNote.length <= 1000) { "Note must be 1,000 characters or fewer." }
        return ValidatedKhataInput(normalizedPerson, direction, MoneyParser.parseRupeesToPaise(amount), opened, due, normalizedNote)
    }

    private fun parseDate(value: String, errorMessage: String): LocalDate = try {
        LocalDate.parse(value.trim())
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException(errorMessage)
    }

    const val LENT = "LENT"
    const val BORROWED = "BORROWED"
}
