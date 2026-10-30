package com.aistudio.dhanam.expense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IncomeInputValidatorTest {
    @Test
    fun validatesAndNormalizesIncomeInputToIntegerPaise() {
        val result = IncomeInputValidator.validate(
            title = "  Monthly salary  ",
            amount = "42500.75",
            category = "Salary",
            date = "2026-10-30",
            note = "  October payroll  "
        )
        assertEquals("Monthly salary", result.title)
        assertEquals(4_250_075L, result.amountPaise)
        assertEquals("Salary", result.category)
        assertEquals("October payroll", result.note)
    }

    @Test
    fun rejectsZeroInvalidCategoryAndInvalidDate() {
        assertThrows(IllegalArgumentException::class.java) {
            IncomeInputValidator.validate("Salary", "0", "Salary", "2026-10-30", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomeInputValidator.validate("Salary", "1000", "Unverified", "2026-10-30", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomeInputValidator.validate("Salary", "1000", "Salary", "30-10-2026", "")
        }
    }

    @Test
    fun rejectsBlankAndOverlongDescriptions() {
        assertThrows(IllegalArgumentException::class.java) {
            IncomeInputValidator.validate(" ", "1000", "Salary", "2026-10-30", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IncomeInputValidator.validate("x".repeat(81), "1000", "Salary", "2026-10-30", "")
        }
    }
}
