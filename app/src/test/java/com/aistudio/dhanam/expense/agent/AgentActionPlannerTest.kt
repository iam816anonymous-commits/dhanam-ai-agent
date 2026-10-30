package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.AgentSkillEntity
import com.aistudio.dhanam.expense.domain.KhataValidator
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

    @Test fun commonTeluguExpenseAndBudgetPhrasesAreSupported() {
        val expense = AgentActionPlanner.parse("భోజనానికి 250 ఖర్చు చేశాను").action as PlannedFinanceAction.AddExpense
        assertEquals(25000L, expense.amountPaise)
        assertEquals("Food", expense.category)
        assertEquals("భోజనం", expense.title)

        val budget = AgentActionPlanner.parse("ఆహార బడ్జెట్ 3000").action as PlannedFinanceAction.SetBudget
        assertEquals(300000L, budget.amountPaise)
        assertEquals("Food", budget.category)
    }

    @Test fun commonSpokenAndShortExpenseFormatsCreatePreviews() {
        val short = AgentActionPlanner.parse("Lunch 250").action as PlannedFinanceAction.AddExpense
        assertEquals("Lunch", short.title)
        assertEquals(25000L, short.amountPaise)
        assertEquals("Food", short.category)

        val reversed = AgentActionPlanner.parse("250 groceries").action as PlannedFinanceAction.AddExpense
        assertEquals("groceries", reversed.title)
        assertEquals("Food", reversed.category)

        val paid = AgentActionPlanner.parse("I paid 120 for bus").action as PlannedFinanceAction.AddExpense
        assertEquals("bus", paid.title)
        assertEquals("Transport", paid.category)
        assertEquals(12000L, paid.amountPaise)

        val bought = AgentActionPlanner.parse("bought vegetables 85").action as PlannedFinanceAction.AddExpense
        assertEquals(8500L, bought.amountPaise)
    }

    @Test fun spendingQueriesAreNotMistakenForNewExpenses() {
        assertEquals(null, AgentActionPlanner.parse("how much did I spend on lunch 250").action)
        assertEquals(null, AgentActionPlanner.parse("show food history 250").action)
    }

    @Test fun approvedExactDescriptionSkillCategorizesAnExpense() {
        val skill = AgentSkillEntity(
            skillKey = "a".repeat(64),
            version = 1,
            title = "Category rule: metro card",
            matchText = "metro card",
            category = "Transport",
            exampleCount = 4,
            testPassCount = 4,
            testTotalCount = 4,
            status = "ACTIVE",
            createdAtEpochMillis = 1L,
            approvedAtEpochMillis = 2L
        )
        val result = AgentActionPlanner.parseWithLearnedSkills("I spent 80 on Metro Card", listOf(skill))
        val action = result.action as PlannedFinanceAction.AddExpense
        assertEquals("metro card", action.title)
        assertEquals("Transport", action.category)
        assertEquals(8_000L, action.amountPaise)
    }

    @Test fun proposedSkillCannotAffectExpenseClassification() {
        val skill = AgentSkillEntity(
            skillKey = "b".repeat(64),
            version = 1,
            title = "Category rule: metro card",
            matchText = "metro card",
            category = "Transport",
            exampleCount = 4,
            testPassCount = 4,
            testTotalCount = 4,
            status = "PROPOSED",
            createdAtEpochMillis = 1L
        )
        val result = AgentActionPlanner.parseWithLearnedSkills("I spent 80 on Metro Card", listOf(skill))
        val action = result.action as PlannedFinanceAction.AddExpense
        assertEquals("Other", action.category)
        assertEquals("Metro Card", action.title)
    }

    @Test fun learnedSkillDoesNotMatchInsideALongerWord() {
        val skill = AgentSkillEntity(
            skillKey = "c".repeat(64),
            version = 1,
            title = "Category rule: metro card",
            matchText = "metro card",
            category = "Transport",
            exampleCount = 4,
            testPassCount = 4,
            testTotalCount = 4,
            status = "ACTIVE",
            createdAtEpochMillis = 1L,
            approvedAtEpochMillis = 2L
        )
        val result = AgentActionPlanner.parseWithLearnedSkills("I spent 80 on Metro Cardigan", listOf(skill))
        val action = result.action as PlannedFinanceAction.AddExpense
        assertEquals("Other", action.category)
        assertEquals("Metro Cardigan", action.title)
    }

    @Test fun lendingCommandCreatesValidatedKhataPreview() {
        val result = AgentActionPlanner.parse("I lent 500 to Ravi")
        val action = result.action as PlannedFinanceAction.AddLoan
        assertEquals("Ravi", action.personName)
        assertEquals(KhataValidator.LENT, action.direction)
        assertEquals(50_000L, action.principalPaise)
    }

    @Test fun borrowingCommandCreatesValidatedKhataPreview() {
        val result = AgentActionPlanner.parse("I borrowed 300 from Ravi")
        val action = result.action as PlannedFinanceAction.AddLoan
        assertEquals("Ravi", action.personName)
        assertEquals(KhataValidator.BORROWED, action.direction)
        assertEquals(30_000L, action.principalPaise)
    }

    @Test fun incompleteLendingCommandAsksForThePersonInsteadOfCreatingAnExpense() {
        val result = AgentActionPlanner.parse("I lent 500")
        assertEquals(null, result.action)
        assertTrue(result.needsClarification)
    }

    @Test fun khataDraftAmountCanBeCorrectedBeforeConfirmation() {
        val initial = AgentActionPlanner.parse("I lent 500 to Ravi").action as PlannedFinanceAction.AddLoan
        val corrected = AgentActionPlanner.applyCorrection(initial, "actually change amount to 750")
        val action = corrected?.action as PlannedFinanceAction.AddLoan
        assertEquals(75_000L, action.principalPaise)
        assertEquals("Ravi", action.personName)
    }

    @Test fun disabledLearnedSkillDoesNotChangeExpenseCategory() {
        val skill = AgentSkillEntity(
            skillKey = "e".repeat(64),
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
        val result = AgentActionPlanner.parseWithLearnedSkills("I spent 80 on Metro Card", listOf(skill))
        val action = result.action as PlannedFinanceAction.AddExpense
        assertEquals("Other", action.category)
    }

}
