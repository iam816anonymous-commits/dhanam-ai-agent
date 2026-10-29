package com.aistudio.dhanam.expense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesWholeRupeesIntoPaise() {
        assertEquals(12_500L, MoneyParser.parseRupeesToPaise("125"))
    }
    @Test fun parsesDecimalAndIndianGrouping() {
        assertEquals(12_345_678L, MoneyParser.parseRupeesToPaise("₹1,23,456.78"))
    }
    @Test fun rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException::class.java) { MoneyParser.parseRupeesToPaise("1.001") }
    }
    @Test fun rejectsZeroNegativeAndMalformedValues() {
        listOf("0", "-1.00", "abc", "").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { MoneyParser.parseRupeesToPaise(raw) }
        }
    }
    @Test fun rejectsOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyParser.parseRupeesToPaise("999999999999999999999999999999999")
        }
    }
    @Test fun formatsPaiseWithoutFloatingPoint() {
        assertEquals("₹1,23,456.78", MoneyParser.formatPaise(12_345_678L))
    }
}
