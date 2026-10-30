package com.aistudio.dhanam.expense.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentArchitecturePolicyTest {
    @Test fun exactlyThreeFinanceSpecialistsAreDefined() {
        assertEquals(
            setOf("FINANCE_OPERATIONS", "FINANCIAL_INTELLIGENCE", "SKILL_IMPROVEMENT"),
            DhanamAgentRole.entries.map { it.name }.toSet()
        )
        DhanamAgentRole.entries.forEach { role ->
            assertTrue(role.label.isNotBlank())
            assertTrue(role.systemInstruction.isNotBlank())
            assertTrue(role.systemInstruction.contains("finance", ignoreCase = true) ||
                role.systemInstruction.contains("financial", ignoreCase = true))
        }
    }

    @Test fun backgroundTasksAreNightGatedButUserOperationsAreNot() {
        val background = AgentTaskRequest(DhanamAgentRole.FINANCIAL_INTELLIGENCE, "summarised ledger")
        val user = AgentTaskRequest(
            DhanamAgentRole.FINANCE_OPERATIONS,
            "parse expense",
            priority = AgentTaskPriority.USER_REQUEST,
            requiresNightWindow = false
        )
        assertTrue(background.requiresNightWindow)
        assertFalse(user.requiresNightWindow)
    }

    @Test fun nightWindowIsOneAmInclusiveAndSixAmExclusive() {
        assertEquals(java.time.LocalTime.of(1, 0), SharedAgentCoordinator.NIGHT_START)
        assertEquals(java.time.LocalTime.of(6, 0), SharedAgentCoordinator.NIGHT_END)
        assertTrue(SharedAgentCoordinator.NIGHT_START.isBefore(SharedAgentCoordinator.NIGHT_END))
    }
}
