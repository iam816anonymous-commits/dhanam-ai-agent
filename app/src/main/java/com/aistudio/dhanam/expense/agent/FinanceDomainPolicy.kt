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
        Regex("""(?i)(?<![\p{L}\p{N}])(?:finance|financial|expense|expenses|spent|spend|spending|income|salary|payroll|budget|khata|ledger|loan|loans|lent|borrow|borrowed|repayment|repayments|debt|money|cash flow|rupees?|rs\.?|upi|transaction|transactions|balance|balances|saving|savings|interest|credit|debit|investment|tax|insurance|bill|bills|receipt|receipts|payment|payments|purchase|purchased|cost|lunch|dinner|breakfast|groceries|grocery|petrol|fuel|bus|transport|shopping|rent|medicine|medical|education|fees|category|categories|merchant|subscription|subscriptions|owed|owe)(?![\p{L}\p{N}])"""),
        Regex("""[₹]"""),
        Regex("""[ఖర్చుఆదాయబడ్జెట్అప్పుఖాతాడబ్బుజీతంవడ్డీచెల్లింపు]"""),
        Regex("""[खर्चआयबजटउधारकर्जपैसावेतनब्याजकिस्त]""")
    )
    private val appTerms = listOf(
        Regex("""(?i)\b(?:dhanam|financial agent|finance agent|agent roles?|local ai|on-device model|offline model|voice input|microphone permission|speech recognition|voice language|model manager|model download|backup|restore|csv export|pdf report|reminder|privacy settings|security settings)\b""")
    )
    private val correctionPattern = Regex(
        """(?i)^(?:actually|change that|correct that|instead|make it|set it|change the amount|change the category|change the description|change description|change title|change item|to\s+₹?\s*[0-9])"""
    )

    fun isSupportedRequest(input: String, hasPendingFinanceAction: Boolean = false): Boolean {
        val normalized = input.trim()
        if (normalized.isEmpty()) return false
        val lower = normalized.lowercase()
        if (lower in helpCommands) return true
        if (hasPendingFinanceAction && correctionPattern.containsMatchIn(normalized)) return true
        if (unsupportedTaskPatterns.any { it.containsMatchIn(normalized) }) return false
        if (financeTerms.any { it.containsMatchIn(normalized) }) return true
        if (appTerms.any { it.containsMatchIn(normalized) }) return true
        if (Regex("""(?i)\b(?:model|agent|assistant|voice|offline|speech)\b""").containsMatchIn(normalized) &&
            Regex("""(?i)\b(?:dhanam|finance|financial|local|on-device|ai)\b""").containsMatchIn(normalized)) return true
        return false
    }
}
