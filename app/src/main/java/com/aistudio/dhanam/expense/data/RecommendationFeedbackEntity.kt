package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** User feedback on a deterministic insight; contains no transaction details or free-form sensitive notes. */
@Entity(
    tableName = "recommendation_feedback",
    indices = [Index(value = ["recommendation_id", "period_month"], unique = true)]
)
data class RecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: String,
    @ColumnInfo(name = "period_month") val periodMonth: String,
    val decision: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long
) {
    init {
        require(recommendationId.isNotBlank() && recommendationId.length <= 120)
        require(periodMonth.matches(Regex("\\d{4}-\\d{2}")))
        require(decision in setOf("HELPFUL", "NOT_USEFUL", "DISMISSED"))
    }
}
