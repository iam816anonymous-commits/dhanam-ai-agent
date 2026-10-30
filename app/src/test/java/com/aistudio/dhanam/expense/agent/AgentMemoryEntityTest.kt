package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.AgentMemoryEntity
import com.aistudio.dhanam.expense.data.AgentMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentMemoryEntityTest {
    @Test
    fun acceptsBoundedRoleSpecificMemory() {
        val memory = AgentMemoryEntity(
            agentRole = "FINANCIAL_INTELLIGENCE",
            memoryKey = "latest_spending_analysis",
            memoryValue = "Food spending increased in the comparable period.",
            sourceReportEpochDay = 20_000L,
            confidencePercent = 80,
            updatedAtEpochMillis = 1_000L
        )
        assertEquals("FINANCIAL_INTELLIGENCE", memory.agentRole)
    }

    @Test
    fun rejectsUnknownRoleAndInvalidMemoryKey() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentMemoryEntity(
                agentRole = "GENERAL_ASSISTANT",
                memoryKey = "latest",
                memoryValue = "Finance only.",
                sourceReportEpochDay = 20_000L,
                confidencePercent = 80,
                updatedAtEpochMillis = 1_000L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentMemoryEntity(
                agentRole = "FINANCE_OPERATIONS",
                memoryKey = "contains spaces",
                memoryValue = "Finance only.",
                sourceReportEpochDay = 20_000L,
                confidencePercent = 80,
                updatedAtEpochMillis = 1_000L
            )
        }
    }

    @Test
    fun rejectsUnboundedMemoryAndInvalidConfidence() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentMemoryEntity(
                agentRole = "SKILL_IMPROVEMENT",
                memoryKey = "latest_skill_review",
                memoryValue = "x".repeat(2_001),
                sourceReportEpochDay = 20_000L,
                confidencePercent = 80,
                updatedAtEpochMillis = 1_000L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentMemoryEntity(
                agentRole = "SKILL_IMPROVEMENT",
                memoryKey = "latest_skill_review",
                memoryValue = "A valid summary.",
                sourceReportEpochDay = 20_000L,
                confidencePercent = 101,
                updatedAtEpochMillis = 1_000L
            )
        }
    }

    @Test
    fun rejectsSelfHandoffAndUnboundedMessage() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentMessageEntity(
                sessionId = "night-20261030",
                messageKey = "self-handoff",
                fromAgent = "FINANCIAL_INTELLIGENCE",
                toAgent = "FINANCIAL_INTELLIGENCE",
                messageType = "ANALYSIS",
                summary = "A valid summary.",
                createdAtEpochMillis = 1_000L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentMessageEntity(
                sessionId = "night-20261030",
                messageKey = "oversized-summary",
                fromAgent = "FINANCE_OPERATIONS",
                toAgent = "FINANCIAL_INTELLIGENCE",
                messageType = "ANALYSIS",
                summary = "x".repeat(1_001),
                createdAtEpochMillis = 1_000L
            )
        }
    }
}
