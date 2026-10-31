package com.aistudio.dhanam.expense.agent

/** Deterministic product boundary: unrelated requests never reach local model inference. */
object FinanceDomainPolicy {
    const val OUT_OF_SCOPE_REPLY =
        "I'm Dhanam, a finance-only assistant. I can help with expenses, income, budgets, Khata, repayments, spending analysis, and questions about Dhanam's financial agents and local models. I can't handle general chat, coding, device control, or unrelated tasks."

    private val helpCommands = setOf("help", "what can you do", "commands", "what are your features")
    private val unsupportedTaskPatterns = listOf(
        Regex("""(?i)\b(?:tell me|make|write|generate)\s+(?:a\s+)?(?:joke|poem|story|song|recipe)\b"""),
        Regex("""(?i)\b(?:write|generate|create|build|debug)\b.{0,50}\b(?:code|script|program|website|software|android app)\b"""),
        Regex("""(?i)\b(?:open|launch|control)\s+(?:my\s+)?(?:phone|camera|wifi|bluetooth|settings|another app)\b"""),
        Regex("""(?i)\b(?:translate this|translation|weather forecast|sports score|news headlines|email draft)\b""")
    )
    private val financeTerms = listOf(
        Regex("""(?i)(?<![\p{L}\p{N}])(?:finance|financial|expense|expenses|spent|spend|spending|paid|earned|received|income|salary|payroll|budget|khata|ledger|loan|loans|lent|borrow|borrowed|repayment|repayments|debt|money|cash flow|rupees?|rs\.?|upi|transaction|transactions|balance|balances|saving|savings|interest|credit|debit|investment|tax|insurance|bill|bills|receipt|receipts|payment|payments|purchase|purchased|cost|lunch|dinner|breakfast|groceries|grocery|petrol|fuel|bus|transport|shopping|rent|medicine|medical|education|fees|category|categories|merchant|subscription|subscriptions|owed|owe|amount)(?![\p{L}\p{N}])"""),
        Regex("""[₹]"""),
        Regex("""(?:ఖర్చు|ఆదాయం|బడ్జెట్|అప్పు|ఖాతా|డబ్బు|జీతం|వడ్డీ|చెల్లింపు|భోజనం|లంచ్|డిన్నర్|టిఫిన్|కిరాణా|రవాణా|పెట్రోల్|బస్సు|ప్రయాణం|షాపింగ్|బట్టలు|బిల్లు|కరెంట్|అద్దె|ఆరోగ్యం|మందులు|వైద్యం|విద్య|ఫీజు|కొన్న)"""),
        Regex("""(?:खर्च|आय|बजट|उधार|कर्ज|पैसा|वेतन|ब्याज|किस्त|भुगतान|कमाई|खरीदा)""")
    )
    private val appTerms = listOf(
        Regex("""(?i)\b(?:what|how|tell me|explain)\b.{0,50}\bdhanam\b"""),
        Regex("""(?i)\bdhanam\b.{0,50}\b(?:agent|model|voice|microphone|speech|backup|restore|csv|pdf|reminder|privacy|security|settings|finance|financial|expense|income|budget|khata)\b"""),
        Regex("""(?i)\b(?:financial|finance)\s+agents?\b"""),
        Regex("""(?i)\b(?:local|on-device|downloaded|verified)\s+(?:ai\s+)?models?\b"""),
        Regex("""(?i)\b(?:what|how|tell me|explain)\b.{0,50}\b(?:model|agent|assistant)\b"""),
        Regex("""(?i)\b(?:model|agent|assistant)\b.{0,50}\b(?:work|role|feature|purpose|finance|financial|not working|error|crash)\b"""),
        Regex("""(?i)\b(?:voice|microphone|speech recognition)\b.{0,40}\b(?:not working|issue|error|permission|crash|language|offline|available)\b"""),
        Regex("""(?i)\b(?:microphone permission|model manager|model download|csv export|pdf report|privacy settings|security settings)\b"""),
        Regex("""(?i)\b(?:backup|restore|reminder)\b.{0,35}\b(?:dhanam|finance|financial|ledger|expense|income)\b""")
    )
    private val correctionPattern = Regex(
        """(?i)^(?:actually|change that|correct that|instead|make it|set it|change the amount|change the category|change the description|change description|change title|change item|to\s+₹?\s*[0-9])"""
    )

    fun isSupportedRequest(input: String, hasPendingFinanceAction: Boolean = false): Boolean {
        val normalized = input.trim().replace(Regex("""(?i)^(?:hey\s+)?dhanam[,!:\s]+"""), "")
        if (normalized.isEmpty()) return false
        val lower = normalized.lowercase()
        if (lower in helpCommands) return true
        if (hasPendingFinanceAction && correctionPattern.containsMatchIn(normalized)) return true
        if (unsupportedTaskPatterns.any { it.containsMatchIn(normalized) }) return false
        if (financeTerms.any { it.containsMatchIn(normalized) }) return true
        if (appTerms.any { it.containsMatchIn(normalized) }) return true
        return false
    }


    fun isSafeModelResponse(message: String): Boolean {
        val normalized = message.trim()
        if (normalized.isEmpty() || normalized.length > 1800) return false
        if (unsupportedTaskPatterns.any { it.containsMatchIn(normalized) }) return false
        if (Regex("""(?i)\b(?:joke|poem|story|song|recipe|weather|code|program|website|device control)\b""").containsMatchIn(normalized)) return false
        return financeTerms.any { it.containsMatchIn(normalized) } ||
            appTerms.any { it.containsMatchIn(normalized) }
    }

    fun isSafeModelClarification(message: String): Boolean {
        val normalized = message.trim()
        if (normalized.isEmpty() || normalized.length > 180) return false
        if (unsupportedTaskPatterns.any { it.containsMatchIn(normalized) }) return false
        if (Regex("""(?i)\b(?:joke|poem|story|song|recipe|weather|code|program|website)\b""").containsMatchIn(normalized)) return false
        return Regex("""(?i)\b(?:finance|financial|expense|income|budget|khata|repayment|amount|category|money|loan|borrow|lend|transaction|record|payment|spending)\b""")
            .containsMatchIn(normalized) ||
            Regex("""(?:ఖర్చు|ఆదాయం|బడ్జెట్|అప్పు|ఖాతా|డబ్బు|మొత్తం)""").containsMatchIn(normalized)
    }
}
