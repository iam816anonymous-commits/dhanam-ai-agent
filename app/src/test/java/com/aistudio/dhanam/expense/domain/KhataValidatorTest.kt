package com.aistudio.dhanam.expense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KhataValidatorTest {
    @Test fun validatesAndNormalizesPersonPhoneAndDates() {
        val value = KhataValidator.validate(
            "  Ravi  ", KhataValidator.LENT, "100.50", "2026-10-01", "2026-10-31", " note ", "+91 98765-43210"
        )
        assertEquals("Ravi", value.personName)
        assertEquals("+91 98765-43210", value.phoneNumber)
        assertEquals(10050L, value.principalPaise)
        assertEquals("note", value.note)
    }

    @Test fun rejectsDueDateBeforeStartAndInvalidPhone() {
        assertThrows(IllegalArgumentException::class.java) {
            KhataValidator.validate("Ravi", KhataValidator.LENT, "100", "2026-10-10", "2026-10-09", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            KhataValidator.validate("Ravi", KhataValidator.LENT, "100", "2026-10-01", "", "", "phone?123")
        }
    }
}
