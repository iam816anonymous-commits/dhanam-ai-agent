package com.aistudio.dhanam.expense.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActionPlannerTest {
    @Test fun expenseRequiresAnAmountAndCreatesAValidatedPreview() {
        val result = AgentActionPlanner.parse("I spent ₹250 on lunch")
        val action = result.action as PlannedFinanceAction.AddExpense
        assertEquals(25000L, action.amountPaise)
        assertEquals("Food", action.category)
        assertEquals("lunch", action.title)
    }

    @Test fun budgetRequiresCategoryAndAmount() {
        assertTrue(AgentActionPlanner.parse("set budget to 3000").needsClarification)
        val action = AgentActionPlanner.parse("set food budget to 3000").action as PlannedFinanceAction.SetBudget
        assertEquals(300000L, action.amountPaise)
        assertEquals("Food", action.category)
    }

    @Test fun vagueCommandsNeverCreateAnAction() {
        assertEquals(null, AgentActionPlanner.parse("do the thing").action)
        assertEquals(null, AgentActionPlanner.parse("record repayment 500 from Ravi").action)
    }
}
