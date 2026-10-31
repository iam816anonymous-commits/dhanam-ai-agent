package com.aistudio.dhanam.expense.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDomainPolicyTest {
    @Test fun permitsSupportedDhanamFinanceWorkflows() {
        listOf(
            "I spent 250 on lunch",
            "salary 50000",
            "set food budget to 3000",
            "I lent 500 to Ravi",
            "record repayment 500 from Ravi",
            "How can I reduce my expenses?",
            "who owes me money?",
            "Tell me about Dhanam AI",
            "What do the financial agents do?",
            "How does the local AI model work?",
            "భోజనానికి 250 ఖర్చు చేశాను"
        ).forEach { assertTrue("Should be finance-scoped: $it", FinanceDomainPolicy.isSupportedRequest(it)) }
    }

    @Test fun blocksGenericAndDeviceAutomationRequestsBeforeModelInference() {
        listOf(
            "tell me a joke",
            "write a poem about money",
            "write code to track my expenses",
            "build an Android app for budgets",
            "open my camera",
            "control WiFi on my phone",
            "what is the weather today",
            "translate this paragraph"
        ).forEach { assertFalse("Should be out of scope: $it", FinanceDomainPolicy.isSupportedRequest(it)) }
    }

    @Test fun pendingFinanceDraftAllowsOnlyRecognisedCorrections() {
        assertTrue(FinanceDomainPolicy.isSupportedRequest("actually change that to 300", hasPendingFinanceAction = true))
        assertTrue(FinanceDomainPolicy.isSupportedRequest("change category to transport", hasPendingFinanceAction = true))
        assertFalse(FinanceDomainPolicy.isSupportedRequest("tell me a joke", hasPendingFinanceAction = true))
    }
}
