package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata_repayments",
    indices = [
        Index(value = ["loan_id", "paid_on_epoch_day"]),
        Index(value = ["request_id"], unique = true, name = "index_khata_repayments_request_id")
    ]
)
data class RepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "loan_id") val loanId: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "paid_on_epoch_day") val paidOnEpochDay: Long,
    val note: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long
)
