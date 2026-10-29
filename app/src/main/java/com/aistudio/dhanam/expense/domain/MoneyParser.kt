package com.aistudio.dhanam.expense.domain

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyParser {
    /** Converts rupees to paise exactly; rejects invalid scale, nonpositive values and overflow. */
    fun parseRupeesToPaise(raw: String): Long {
        val normalized = raw.trim().replace("₹", "").replace(",", "").replace(" ", "")
        require(normalized.isNotEmpty()) { "Enter an amount." }
        require(!normalized.startsWith("-")) { "Amount must be greater than zero." }
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Enter a valid amount, for example 125.50.")
        }
        require(decimal.signum() > 0) { "Amount must be greater than zero." }
        val paise = try {
            decimal.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Amount must have at most two decimal places and fit the supported range.")
        }
        require(paise > 0) { "Amount must be greater than zero." }
        return paise
    }

    fun formatPaise(amountPaise: Long): String {
        require(amountPaise >= 0) { "Amount cannot be negative." }
        val rupees = amountPaise / 100
        val paise = amountPaise % 100
        val digits = rupees.toString()
        val grouped = if (digits.length <= 3) digits else {
            val lastThree = digits.takeLast(3)
            val prefix = digits.dropLast(3)
            prefix.reversed().chunked(2).joinToString(",") { it.reversed() }
                .reversed().let { "$it,$lastThree" }
        }
        return "₹$grouped.%02d".format(java.util.Locale.ROOT, paise)
    }
}
