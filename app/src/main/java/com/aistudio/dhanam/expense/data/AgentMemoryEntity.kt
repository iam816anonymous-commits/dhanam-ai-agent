package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bounded, role-isolated memory containing deterministic summaries, never raw prompts or executable instructions. */
@Entity(
    tableName = "agent_memories",
    indices = [
        Index(value = ["agent_role", "memory_key"], unique = true),
        Index(value = ["updated_at_epoch_millis"])
    ]
)
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "agent_role") val agentRole: String,
    @ColumnInfo(name = "memory_key") val memoryKey: String,
    @ColumnInfo(name = "memory_value") val memoryValue: String,
    @ColumnInfo(name = "source_report_epoch_day") val sourceReportEpochDay: Long,
    @ColumnInfo(name = "confidence_percent") val confidencePercent: Int,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long
) {
    init {
        require(agentRole in AGENT_ROLES) { "Unknown finance agent role." }
        require(memoryKey.matches(Regex("[a-z0-9_]{1,80}"))) { "Invalid agent memory key." }
        require(memoryValue.isNotBlank() && memoryValue.length <= 2_000) { "Agent memory must contain 1–2,000 characters." }
        require(confidencePercent in 0..100) { "Memory confidence must be between 0 and 100." }
    }

    companion object {
        val AGENT_ROLES = setOf("FINANCE_OPERATIONS", "FINANCIAL_INTELLIGENCE", "SKILL_IMPROVEMENT")
    }
}
