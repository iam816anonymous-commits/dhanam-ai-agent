package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "report_date_epoch_day") val reportDateEpochDay: Long,
    @ColumnInfo(name = "run_status") val runStatus: String,
    @ColumnInfo(name = "operations_summary") val operationsSummary: String,
    @ColumnInfo(name = "intelligence_summary") val intelligenceSummary: String,
    @ColumnInfo(name = "skill_summary") val skillSummary: String,
    @ColumnInfo(name = "next_improvements") val nextImprovements: String,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long
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
