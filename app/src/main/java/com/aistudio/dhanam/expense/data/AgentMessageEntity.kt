package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local audit trail of bounded specialist handoffs. It intentionally stores summaries only,
 * never raw transactions, model prompts, credentials or arbitrary instructions.
 */
@Entity(
    tableName = "agent_messages",
    indices = [
        Index(value = ["session_id", "created_at_epoch_millis"]),
        Index(value = ["created_at_epoch_millis"]),
        Index(value = ["message_key"], unique = true)
    ]
)
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "message_key") val messageKey: String,
    @ColumnInfo(name = "from_agent") val fromAgent: String,
    @ColumnInfo(name = "to_agent") val toAgent: String,
    @ColumnInfo(name = "message_type") val messageType: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long
) {
    init {
        require(sessionId.length in 1..40)
        require(messageKey.length in 1..160)
        require(fromAgent in AgentMemoryEntity.AGENT_ROLES)
        require(toAgent in AgentMemoryEntity.AGENT_ROLES && toAgent != fromAgent)
        require(messageType.matches(Regex("[A-Z_]{1,40}")))
        require(summary.isNotBlank() && summary.length <= 1_000)
    }
}
