package com.aistudio.dhanam.expense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyMoneyConverterTest {
    @Test fun roundsLegacyDoubleToNearestPaiseWithHalfUpPolicy() {
        assertEquals(10001L, LegacyMoneyConverter.toPaise(100.005))
        assertEquals(1234L, LegacyMoneyConverter.toPaise(12.34))
        assertEquals(0L, LegacyMoneyConverter.toPaise(0.0, allowZero = true))
    }

    @Test fun rejectsNonFiniteNegativeAndOverflowValues() {
        assertThrows(IllegalArgumentException::class.java) { LegacyMoneyConverter.toPaise(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { LegacyMoneyConverter.toPaise(-1.0) }
        assertThrows(ArithmeticException::class.java) { LegacyMoneyConverter.toPaise(Double.MAX_VALUE) }
    }
}
