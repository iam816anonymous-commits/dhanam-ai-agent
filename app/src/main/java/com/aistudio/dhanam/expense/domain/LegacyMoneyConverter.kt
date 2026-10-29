package com.aistudio.dhanam.expense.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Explicit conversion policy for legacy rupee Double fields: decimal HALF_UP to the nearest paise. */
object LegacyMoneyConverter {
    fun toPaise(value: Double, allowZero: Boolean = false): Long {
        require(value.isFinite()) { "amount is not finite" }
        require(if (allowZero) value >= 0.0 else value > 0.0) {
            if (allowZero) "amount must be zero or greater" else "amount must be greater than zero"
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }
}
