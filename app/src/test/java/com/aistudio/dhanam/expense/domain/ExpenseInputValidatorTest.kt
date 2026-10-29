package com.aistudio.dhanam.expense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpenseInputValidatorTest {
    @Test fun trimsAndValidatesAValidExpense() {
        val input = ExpenseInputValidator.validate("  Groceries  ", "250.50", "Food", "2026-10-29", "  weekly shopping  ")
        assertEquals("Groceries", input.title)
        assertEquals(25_050L, input.amountPaise)
        assertEquals("weekly shopping", input.note)
        assertEquals("2026-10-29", input.occurredOn.toString())
    }
    @Test fun rejectsMissingTitleAndUnknownCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseInputValidator.validate("", "10", "Food", "2026-10-29", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseInputValidator.validate("Tea", "10", "Random", "2026-10-29", "")
        }
    }
    @Test fun rejectsInvalidCalendarDate() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseInputValidator.validate("Tea", "10", "Food", "2026-02-30", "")
        }
    }
}
