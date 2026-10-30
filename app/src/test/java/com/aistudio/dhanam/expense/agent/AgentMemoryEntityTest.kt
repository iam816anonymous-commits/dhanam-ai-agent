package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.AgentMemoryEntity
import com.aistudio.dhanam.expense.data.AgentMessageEntity
import com.aistudio.dhanam.expense.data.AgentSkillEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemoryEntityTest {
    @Test fun storesBoundedMemoryForEachFinanceRole() {
        val roles = AgentMemoryEntity.AGENT_ROLES
        assertEquals(3, roles.size)
        roles.forEach { role ->
            val memory = AgentMemoryEntity(
                agentRole = role,
                memoryKey = "nightly_summary",
                memoryValue = "Verified local summary for $role.",
                sourceReportEpochDay = 20_000L,
                confidencePercent = 95,
                updatedAtEpochMillis = 1L
            )
            assertEquals(role, memory.agentRole)
        }
    }

    @Test fun rejectsGenericOrUnknownAgentMemoryRoles() {
        val result = runCatching {
            AgentMemoryEntity(
                agentRole = "GENERAL_ASSISTANT",
                memoryKey = "generic",
                memoryValue = "Do unrelated tasks.",
                sourceReportEpochDay = 20_000L,
                confidencePercent = 100,
                updatedAtEpochMillis = 1L
            )
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsUnboundedMemoryValues() {
        val result = runCatching {
            AgentMemoryEntity(
                agentRole = "FINANCIAL_INTELLIGENCE",
                memoryKey = "spending_patterns",
                memoryValue = "x".repeat(2_001),
                sourceReportEpochDay = 20_000L,
                confidencePercent = 100,
                updatedAtEpochMillis = 1L
            )
        }
        assertTrue(result.isFailure)
    }

    @Test fun handoffsMustBeBetweenDifferentFinanceAgents() {
        val result = runCatching {
            AgentMessageEntity(
                sessionId = "2026-10-30",
                messageKey = "same-role",
                fromAgent = "FINANCE_OPERATIONS",
                toAgent = "FINANCE_OPERATIONS",
                messageType = "LEDGER_REVIEW",
                summary = "A bounded summary.",
                createdAtEpochMillis = 1L
            )
        }
        assertTrue(result.isFailure)
    }

    @Test fun disabledSkillsRemainValidForRollback() {
        val skill = AgentSkillEntity(
            skillKey = "d".repeat(64),
            version = 1,
            title = "Category rule: metro card",
            matchText = "metro card",
            category = "Transport",
            exampleCount = 4,
            testPassCount = 4,
            testTotalCount = 4,
            status = "DISABLED",
            createdAtEpochMillis = 1L
        )
        assertEquals("DISABLED", skill.status)
    }
}
