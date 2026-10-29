package com.aistudio.dhanam.expense.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category", "month_epoch_day"], unique = true, name = "index_budgets_category_month_epoch_day")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    @androidx.room.ColumnInfo(name = "month_epoch_day") val monthEpochDay: Long,
    @androidx.room.ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @androidx.room.ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @androidx.room.ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long
)
