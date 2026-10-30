package com.aistudio.dhanam.expense.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stores summaries and outcomes only; never stores raw transaction descriptions or model prompts. */
@Entity(
    tableName = "agent_learning_reports",
    indices = [Index(value = ["report_date_epoch_day"], unique = true)]
)
data class AgentLearningReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportDateEpochDay: Long,
    val runStatus: String,
    val operationsSummary: String,
    val intelligenceSummary: String,
    val skillSummary: String,
    val nextImprovements: String,
    val durationMillis: Long,
    val createdAtEpochMillis: Long
) {
    init {
        require(runStatus.length <= 32)
        require(operationsSummary.length <= 2_000)
        require(intelligenceSummary.length <= 4_000)
        require(skillSummary.length <= 2_000)
        require(nextImprovements.length <= 2_000)
        require(durationMillis >= 0)
    }
}
