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
    @Test fun strictModelProposalIsValidatedBeforeItCanBecomeAnAction() {
        val valid = AgentActionPlanner.parseModelProposal(
            """{"intent":"expense","amount":"250","title":"Lunch","category":"Food","message":""}"""
        )
        val action = valid.action as PlannedFinanceAction.AddExpense
        assertEquals(25000L, action.amountPaise)
        assertEquals("Lunch", action.title)

        assertEquals(null, AgentActionPlanner.parseModelProposal(
            """{"intent":"repayment","amount":"250","title":"Lunch","category":"Food","message":""}"""
        ).action)
        assertEquals(null, AgentActionPlanner.parseModelProposal(
            """{"intent":"expense","amount":"250","title":"Lunch","category":"Crypto","message":""}"""
        ).action)
    }

    @Test fun correctionsUpdateOnlyTheStagedDraft() {
        val original = AgentActionPlanner.parse("I spent 250 on lunch").action as PlannedFinanceAction.AddExpense
        val amountCorrection = AgentActionPlanner.applyCorrection(original, "actually change that to 300")!!.action as PlannedFinanceAction.AddExpense
        assertEquals(30000L, amountCorrection.amountPaise)
        assertEquals("lunch", amountCorrection.title)

        val categoryCorrection = AgentActionPlanner.applyCorrection(original, "change category to transport")!!.action as PlannedFinanceAction.AddExpense
        assertEquals("Transport", categoryCorrection.category)

        val titleCorrection = AgentActionPlanner.applyCorrection(original, "change description to taxi")!!.action as PlannedFinanceAction.AddExpense
        assertEquals("taxi", titleCorrection.title)
        assertEquals(25000L, titleCorrection.amountPaise)
    }

    @Test fun modelClarificationDoesNotCreateAnAction() {
        val result = AgentActionPlanner.parseModelProposal(
            """{"intent":"clarify","amount":"","title":"","category":"","message":"What amount?"}"""
        )
        assertEquals(null, result.action)
        assertTrue(result.needsClarification)
        assertEquals("What amount?", result.message)
    }

}
