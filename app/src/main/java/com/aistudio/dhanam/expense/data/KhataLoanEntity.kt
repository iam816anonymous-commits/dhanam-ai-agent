package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "khata_loans",
    indices = [Index(value = ["person_name"]), Index(value = ["due_on_epoch_day"]), Index(value = ["legacy_source_id"], unique = true, name = "index_khata_loans_legacy_source_id")]
)
data class KhataLoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_name") val personName: String,
    /** LENT means this person owes the user; BORROWED means the user owes this person. */
    val direction: String,
    @ColumnInfo(name = "principal_paise") val principalPaise: Long,
    @ColumnInfo(name = "paid_paise") val paidPaise: Long,
    @ColumnInfo(name = "opened_on_epoch_day") val openedOnEpochDay: Long,
    @ColumnInfo(name = "due_on_epoch_day") val dueOnEpochDay: Long?,
    val note: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "phone_number", defaultValue = "''") val phoneNumber: String = "",
    @ColumnInfo(name = "is_settled", defaultValue = "0") val isSettled: Boolean = false,
    @ColumnInfo(name = "legacy_source_id") val legacySourceId: String? = null
) {
    val outstandingPaise: Long get() = if (isSettled) 0L else (principalPaise - paidPaise).coerceAtLeast(0L)
}
