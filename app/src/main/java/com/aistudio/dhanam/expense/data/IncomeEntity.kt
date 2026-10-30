package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Income is stored separately from expenses; amounts are integer paise and dates are epoch days. */
@Entity(
    tableName = "income_records",
    indices = [
        Index(value = ["occurred_on_epoch_day"]),
        Index(value = ["category"]),
        Index(value = ["source_record_key"], unique = true, name = "index_income_records_source_record_key")
    ]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val category: String,
    @ColumnInfo(name = "occurred_on_epoch_day") val occurredOnEpochDay: Long,
    val note: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "source_record_key") val sourceRecordKey: String? = null
)
