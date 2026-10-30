package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import com.aistudio.dhanam.expense.data.AgentSkillEntity
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.domain.IncomeInputValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.time.LocalDate
import java.time.YearMonth

sealed interface PlannedFinanceAction {
    val summary: String
    data class AddExpense(val title: String, val amountPaise: Long, val category: String, val date: LocalDate, val note: String = "") : PlannedFinanceAction {
        override val summary = "Record expense: $title · ${MoneyParser.formatPaise(amountPaise)} · $category · $date"
    }
    data class AnalyzeSpending(val month: YearMonth = YearMonth.now()) : PlannedFinanceAction {
        override val summary = "Analyze recorded spending for $month"
    }
    data class AddIncome(
        val title: String,
        val amountPaise: Long,
        val category: String,
        val date: LocalDate,
        val note: String = ""
    ) : PlannedFinanceAction {
        override val summary = "Record income: $title · ${MoneyParser.formatPaise(amountPaise)} · $category · $date"
    }
    data class SetBudget(val category: String, val amountPaise: Long, val month: YearMonth) : PlannedFinanceAction {
        override val summary = "Set ${category} budget to ${MoneyParser.formatPaise(amountPaise)} for ${month}"
    }
    data class AddLoan(
        val personName: String,
        val direction: String,
        val principalPaise: Long,
        val openedOn: LocalDate,
        val dueOn: LocalDate? = null,
        val note: String = "",
        val phoneNumber: String = ""
    ) : PlannedFinanceAction {
        override val summary = if (direction == KhataValidator.LENT) {
            "Record money lent to $personName · ${MoneyParser.formatPaise(principalPaise)} · $openedOn"
        } else {
            "Record money borrowed from $personName · ${MoneyParser.formatPaise(principalPaise)} · $openedOn"
        }
    }
    data class RecordRepayment(
        val personName: String, val amountPaise: Long, val loanId: Long,
        val date: LocalDate, val requestId: String, val direction: String = KhataValidator.LENT
    ) : PlannedFinanceAction {
        override val summary = "Record repayment of ${MoneyParser.formatPaise(amountPaise)} for ${personName} on ${date}"
    }
}

data class PlannerResult(val action: PlannedFinanceAction? = null, val message: String, val needsClarification: Boolean = false)

object AgentActionPlanner {
    private val amountPattern = Regex("""(?:₹\s*)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val categories = mapOf(
        "food" to "Food", "lunch" to "Food", "dinner" to "Food", "breakfast" to "Food", "grocery" to "Food", "groceries" to "Food",
        "transport" to "Transport", "fuel" to "Transport", "petrol" to "Transport", "bus" to "Transport", "travel" to "Transport",
        "shopping" to "Shopping", "clothes" to "Shopping", "bill" to "Bills", "bills" to "Bills", "electricity" to "Bills", "rent" to "Bills",
        "health" to "Health", "medicine" to "Health", "medical" to "Health", "education" to "Education", "fee" to "Education", "other" to "Other"
    )

    /** Uses only explicitly approved, exact-description category rules. */
    fun parseWithLearnedSkills(text: String, activeSkills: List<AgentSkillEntity>): PlannerResult {
        if (activeSkills.isEmpty()) return parse(text)
        val input = text.trim().replace(Regex("""\s+"""), " ").take(1000)
        val lower = input.lowercase()
        if (input.isEmpty() || lower.contains("budget") || lower.contains("repayment") ||
            parseIncomeCommand(input) != null || parseFinancialQuery(input) != null ||
            listOf("how much", "what did i spend", "show ", "list ", "history", "what is").any { lower.startsWith(it) }) {
            return parse(text)
        }
        val skill = activeSkills.asSequence()
            .filter { it.status == "ACTIVE" }
            .sortedByDescending { it.matchText.length }
            .firstOrNull { skill ->
                Regex(
                    """(?<![\p{L}\p{N}])${Regex.escape(skill.matchText)}(?![\p{L}\p{N}])""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(input)
            }
            ?: return parse(text)
        val amount = amountPattern.findAll(input).lastOrNull()?.groupValues?.get(1) ?: return parse(text)
        val hasExpenseVerb = listOf("spent", "spend", "expense", "bought", "buy", "paid", "purchase", "purchased", "add", "record", "log", "note", "cost")
            .any { lower.contains(it) }
        val remainder = lower
            .replace(amountPattern.findAll(input).last().value.lowercase(), " ")
            .replace(Regex("""[₹,]"""), " ")
            .replace(Regex("""\b(?:i|my|spent|spend|expense|add|record|log|note|bought|buy|paid|purchase|purchased|for|on|to|the|a|an|rupees|rs)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!hasExpenseVerb && remainder != skill.matchText.lowercase()) return parse(text)
        return try {
            val validated = ExpenseInputValidator.validate(
                skill.matchText, amount, skill.category, LocalDate.now().toString(), ""
            )
            PlannerResult(
                PlannedFinanceAction.AddExpense(
                    validated.title, validated.amountPaise, validated.category, validated.occurredOn
                ),
                "I matched “${skill.matchText}” using your approved local category skill. Review the expense before saving."
            )
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "I could not validate this learned expense entry.", needsClarification = true)
        }
    }

    private fun parseFinancialQuery(input: String): PlannerResult? {
        val lower = input.lowercase()
        if (lower.contains("budget")) return null
        val queryPhrases = listOf(
            "analyze", "analysis", "reduce my spending", "reduce expenses", "save money",
            "save on", "overspend", "spending pattern", "spending trend", "financial summary",
            "cash flow", "how much did i spend", "what are my biggest expenses",
            "where is my money going", "how can i save", "how can i reduce"
        )
        if (queryPhrases.none { lower.contains(it) }) return null
        return PlannerResult(
            PlannedFinanceAction.AnalyzeSpending(),
            "I'll review your recorded expenses, budgets and Khata balances. This is read-only; no financial records will be changed."
        )
    }

    private fun parseIncomeCommand(input: String): PlannerResult? {
        val lower = input.lowercase()
        if (listOf("how much", "what is", "show ", "list ", "history", "total ").any { lower.startsWith(it) }) return null
        val categoryAliases = mapOf(
            "salary" to "Salary", "payroll" to "Salary",
            "business" to "Business",
            "freelance" to "Freelance", "freelancing" to "Freelance",
            "investment" to "Investment", "interest" to "Investment", "dividend" to "Investment",
            "gift" to "Gift", "other" to "Other"
        )
        val categoryTerm = categoryAliases.keys.sortedByDescending { it.length }
            .firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lower) }
        val hasExpenseVerb = listOf("spent", "spend", "expense", "bought", "buy", "purchase", "purchased", "payment", "paying")
            .any { lower.contains(it) } || (lower.contains("paid") && !lower.contains("got paid"))
        if (hasExpenseVerb) return null
        val hasIncomeVerb = Regex("""\b(?:income|earned|received|got paid|salary credited|credited salary)\b""")
            .containsMatchIn(lower)
        val isBareIncomeSource = categoryTerm != null && lower.startsWith(categoryTerm)
        if (!hasIncomeVerb && !isBareIncomeSource) return null
        val amount = amountPattern.findAll(input).lastOrNull()?.groupValues?.get(1)
            ?: return PlannerResult(message = "What amount of income should I record?", needsClarification = true)
        val category = categoryTerm?.let { categoryAliases.getValue(it) }
            ?: return PlannerResult(
                message = "Which income source is this: Salary, Business, Freelance, Investment, Gift or Other?",
                needsClarification = true
            )
        val title = when (category) {
            "Salary" -> "Salary"
            "Business" -> "Business income"
            "Freelance" -> "Freelance income"
            "Investment" -> "Investment income"
            "Gift" -> "Gift"
            else -> "Other income"
        }
        return try {
            val validated = IncomeInputValidator.validate(title, amount, category, LocalDate.now().toString(), "")
            PlannerResult(
                PlannedFinanceAction.AddIncome(validated.title, validated.amountPaise, validated.category, validated.occurredOn),
                "Review this income entry before saving."
            )
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "I could not validate this income entry.", needsClarification = true)
        }
    }

    private fun parseLoanCommand(input: String): PlannerResult? {
        val pattern = Regex("""(?i)^(?:i\s+)?(lent|loaned|gave|borrowed|took)\s+(?:₹\s*)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+(to|from)\s+(.+)$""")
        val match = pattern.matchEntire(input) ?: return null
        val verb = match.groupValues[1].lowercase()
        val amount = match.groupValues[2]
        val preposition = match.groupValues[3].lowercase()
        val person = match.groupValues[4].trim().trimEnd('.', '!', '?').take(100)
        val direction = when {
            verb in setOf("lent", "loaned", "gave") && preposition == "to" -> KhataValidator.LENT
            verb in setOf("borrowed", "took") && preposition == "from" -> KhataValidator.BORROWED
            else -> return PlannerResult(
                message = "Please clarify whether you lent money to this person or borrowed money from them.",
                needsClarification = true
            )
        }
        return try {
            val validated = KhataValidator.validate(
                person, direction, amount, LocalDate.now().toString(), "", "", ""
            )
            PlannerResult(
                PlannedFinanceAction.AddLoan(
                    personName = validated.personName,
                    direction = validated.direction,
                    principalPaise = validated.principalPaise,
                    openedOn = validated.openedOn,
                    dueOn = validated.dueOn,
                    note = validated.note,
                    phoneNumber = validated.phoneNumber
                ),
                "Review this Khata entry before saving."
            )
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "I could not validate this Khata entry.", needsClarification = true)
        }
    }

    fun parse(text: String): PlannerResult {
        val input = text.trim().replace(Regex("""\s+"""), " ").take(1000)
        if (input.isEmpty()) return PlannerResult(message = "Tell me what you want to do.")
        val lower = input.lowercase()
        parseTelugu(input)?.let { return it }
        parseLoanCommand(input)?.let { return it }
        parseIncomeCommand(input)?.let { return it }
        parseFinancialQuery(input)?.let { return it }
        if (lower.startsWith("i lent") || lower.startsWith("lent ") ||
            lower.startsWith("i borrowed") || lower.startsWith("borrowed ")) {
            return PlannerResult(message = "I can record this in Khata. Specify both the amount and the person's name, for example “I lent 500 to Ravi” or “I borrowed 500 from Ravi”.", needsClarification = true)
        }
        if (lower in setOf("help", "what can you do", "commands")) {
            return PlannerResult(message = "Try “Lunch 250”, “I spent 250 on lunch”, “salary 50000”, “I lent 500 to Ravi”, “I borrowed 300 from Ravi”, “set food budget to 3000”, “record repayment 500 from Ravi”, or “record repayment 300 to Ravi”. I'll preview every change before saving.")
        }
        if (lower.contains("budget")) {
            val amount = amountPattern.findAll(input).lastOrNull()?.groupValues?.get(1)
                ?: return PlannerResult(message = "What amount should the budget be?", needsClarification = true)
            val categoryWord = categories.keys.sortedByDescending { it.length }
                .firstOrNull { Regex("\b" + Regex.escape(it) + "\b").containsMatchIn(lower) }
                ?: return PlannerResult(message = "Which category is this budget for? Choose Food, Transport, Shopping, Bills, Health, Education or Other.", needsClarification = true)
            return try {
                val paise = MoneyParser.parseRupeesToPaise(amount)
                PlannerResult(PlannedFinanceAction.SetBudget(categories.getValue(categoryWord), paise, YearMonth.now()), "Review this budget change before saving.")
            } catch (e: Exception) {
                PlannerResult(message = e.message ?: "Check the budget amount.", needsClarification = true)
            }
        }
        if (listOf("how much", "what did i spend", "show ", "list ", "history", "total spent", "remaining budget").any { lower.startsWith(it) }) {
            return PlannerResult(message = "That sounds like a question about your saved financial records. Open Insights for recorded income, spending and budgets, or ask me a more specific question.", needsClarification = true)
        }
        if (lower.contains("repayment") || lower.contains("pay back") || lower.contains("paid back") ||
            lower.contains("returned") || lower.contains("చెల్లించాడు") || lower.contains("తిరిగి ఇచ్చాడు")) {
            return PlannerResult(message = "I need to match the repayment to exactly one open Khata record before I can prepare it. Use “record repayment 500 from Ravi” and confirm the matching person in the preview.", needsClarification = true)
        }

        val amountMatch = amountPattern.findAll(input).firstOrNull()
            ?: return PlannerResult(message = "I couldn't find an amount. Try “I spent 250 on lunch” or “Lunch 250”.", needsClarification = true)
        val categoryWord = categories.keys.sortedByDescending { it.length }
            .firstOrNull { Regex("\b" + Regex.escape(it) + "\b").containsMatchIn(lower) }
        val hasExpenseVerb = listOf(
            "spent", "spend", "expense", "bought", "buy", "paid", "purchase", "purchased",
            "add", "record", "log", "note", "cost", "ఖర్చు", "కొన్న", "చెల్లించ"
        ).any { lower.contains(it) }
        val looksLikeBareEntry = categoryWord != null &&
            !listOf("how much", "what is", "show", "list", "history", "budget", "remaining", "total").any { lower.contains(it) }
        if (!hasExpenseVerb && !looksLikeBareEntry) {
            return PlannerResult(message = "I can prepare an expense or budget change. Try “I spent 250 on lunch” or “Lunch 250”.", needsClarification = true)
        }
        val category = categoryWord?.let { categories.getValue(it) } ?: "Other"
        val title = when {
            Regex("""(?i)\b(?:on|for)\s+(.+)$""").containsMatchIn(input) -> {
                Regex("""(?i)\b(?:on|for)\s+(.+)$""").find(input)!!.groupValues[1]
                    .replace(Regex("""[.!?]$"""), "").take(120)
            }
            else -> input
                .replace(amountMatch.value, " ")
                .replace(Regex("""(?i)\b(?:i|my|spent|spend|expense|add|record|log|note|bought|buy|paid|purchase|purchased|for|on|rupees|rs)\b"""), " ")
                .replace(Regex("""[₹,]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim().take(120).ifBlank { category }
        }
        return try {
            val validated = ExpenseInputValidator.validate(title, amountMatch.groupValues[1], category, LocalDate.now().toString(), "")
            PlannerResult(PlannedFinanceAction.AddExpense(validated.title, validated.amountPaise, validated.category, validated.occurredOn), "Review the expense before saving.")
        } catch (e: Exception) {
            PlannerResult(message = e.message ?: "I couldn't validate that expense.", needsClarification = true)
        }
    }

    private val teluguCategoryTerms = listOf(
        Triple("ఆహారం", "Food", "భోజనం"),
        Triple("భోజనం", "Food", "భోజనం"),
        Triple("భోజన", "Food", "భోజనం"),
        Triple("ఆహార", "Food", "భోజనం"),
        Triple("లంచ్", "Food", "లంచ్"),
        Triple("డిన్నర్", "Food", "డిన్నర్"),
        Triple("టిఫిన్", "Food", "టిఫిన్"),
        Triple("కిరాణా", "Food", "కిరాణా"),
        Triple("రవాణా", "Transport", "ప్రయాణం"),
        Triple("పెట్రోల్", "Transport", "పెట్రోల్"),
        Triple("బస్సు", "Transport", "బస్సు"),
        Triple("ప్రయాణం", "Transport", "ప్రయాణం"),
        Triple("షాపింగ్", "Shopping", "షాపింగ్"),
        Triple("బట్టలు", "Shopping", "బట్టలు"),
        Triple("బిల్లు", "Bills", "బిల్లు"),
        Triple("కరెంట్", "Bills", "కరెంట్"),
        Triple("అద్దె", "Bills", "అద్దె"),
        Triple("ఆరోగ్యం", "Health", "మందులు"),
        Triple("మందులు", "Health", "మందులు"),
        Triple("వైద్యం", "Health", "వైద్యం"),
        Triple("విద్య", "Education", "విద్య"),
        Triple("ఫీజు", "Education", "ఫీజు"),
        Triple("ఇతర", "Other", "ఇతర ఖర్చు")
    )

    /** Deterministic starter coverage for common Telugu expense and budget phrases. */
    private fun parseTelugu(input: String): PlannerResult? {
        if (!Regex("""[\u0C00-\u0C7F]""").containsMatchIn(input)) return null
        val lower = input.lowercase()
        val amount = amountPattern.findAll(input).lastOrNull()?.groupValues?.get(1)
            ?: return PlannerResult(message = "దయచేసి మొత్తాన్ని అంకెల్లో చెప్పండి. ఉదాహరణ: భోజనానికి 250 ఖర్చు చేశాను.", needsClarification = true)
        val term = teluguCategoryTerms.firstOrNull { lower.contains(it.first) }
            ?: return PlannerResult(message = "దయచేసి ఖర్చు లేదా బడ్జెట్ వర్గాన్ని స్పష్టంగా చెప్పండి: ఆహారం, ప్రయాణం, షాపింగ్, బిల్లులు, ఆరోగ్యం లేదా విద్య.", needsClarification = true)
        return try {
            val paise = MoneyParser.parseRupeesToPaise(amount)
            if (lower.contains("బడ్జెట్")) {
                PlannerResult(PlannedFinanceAction.SetBudget(term.second, paise, YearMonth.now()), "బడ్జెట్ మార్పును సేవ్ చేసే ముందు పరిశీలించండి.")
            } else if (lower.contains("ఖర్చు") || lower.contains("కొన్న") || lower.contains("చెల్లించ")) {
                val validated = ExpenseInputValidator.validate(term.third, amount, term.second, LocalDate.now().toString(), "")
                PlannerResult(PlannedFinanceAction.AddExpense(validated.title, validated.amountPaise, validated.category, validated.occurredOn), "ఖర్చు వివరాలను పరిశీలించి నిర్ధారించిన తర్వాతే సేవ్ చేస్తాను.")
            } else {
                PlannerResult(message = "మీరు ఖర్చు నమోదు చేయాలనుకుంటున్నారా లేదా బడ్జెట్ మార్చాలనుకుంటున్నారా? దయచేసి స్పష్టంగా చెప్పండి.", needsClarification = true)
            }
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "మొత్తాన్ని తనిఖీ చేయండి.", needsClarification = true)
        }
    }

    /**
     * Model output is untrusted. Only a small JSON schema is accepted, and every field is
     * validated again before a pending action can be shown to the user.
     */
    fun parseModelProposal(raw: String): PlannerResult {
        val json = Regex("""\{[\s\S]*?}""").find(raw)?.value
            ?: return PlannerResult(message = "I couldn't safely interpret that request. Please specify a financial record or budget change.", needsClarification = true)
        fun field(name: String): String? {
            val pattern = Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"([^\"]*)\"")
            return pattern.find(json)?.groupValues?.get(1)?.trim()
        }
        val intent = field("intent")?.lowercase()
        if (intent == "clarify") {
            return PlannerResult(
                message = field("message")?.take(180)?.takeIf { it.isNotBlank() }
                    ?: "Please clarify the financial action and amount.",
                needsClarification = true
            )
        }
        if (intent !in setOf("expense", "income", "budget")) {
            return PlannerResult(message = "The local model returned an unsupported finance action. No change was made.", needsClarification = true)
        }
        val amount = field("amount")?.takeIf { it.isNotBlank() }
            ?: return PlannerResult(message = "What amount should I use?", needsClarification = true)
        val date = field("date")?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
        return try {
            val paise = MoneyParser.parseRupeesToPaise(amount)
            if (intent == "income") {
                val categoryRaw = field("category")?.takeIf { it.isNotBlank() }
                    ?: return PlannerResult(message = "Which income source category should I use?", needsClarification = true)
                val category = IncomeInputValidator.categories.firstOrNull { it.equals(categoryRaw, ignoreCase = true) }
                    ?: return PlannerResult(message = "Choose Salary, Business, Freelance, Investment, Gift or Other for this income.", needsClarification = true)
                val title = field("title")?.takeIf { it.isNotBlank() } ?: category
                val validated = IncomeInputValidator.validate(title, amount, category, date, "")
                PlannerResult(PlannedFinanceAction.AddIncome(validated.title, validated.amountPaise, validated.category, validated.occurredOn),
                    "Review the income entry before saving.")
            } else {
                val categoryRaw = field("category")?.takeIf { it.isNotBlank() }
                    ?: return PlannerResult(message = "Which expense or budget category should I use?", needsClarification = true)
                val category = ExpenseInputValidator.categories.firstOrNull { it.equals(categoryRaw, ignoreCase = true) }
                    ?: return PlannerResult(message = "The proposed category is not supported. Choose Food, Transport, Shopping, Bills, Health, Education or Other.", needsClarification = true)
                if (intent == "budget") {
                    PlannerResult(PlannedFinanceAction.SetBudget(category, paise, YearMonth.now()), "Review this budget change before saving.")
                } else {
                    val title = field("title")?.takeIf { it.isNotBlank() }
                        ?: return PlannerResult(message = "What is the expense description?", needsClarification = true)
                    val validated = ExpenseInputValidator.validate(title, amount, category, date, "")
                    PlannerResult(PlannedFinanceAction.AddExpense(validated.title, validated.amountPaise, validated.category, validated.occurredOn),
                        "Review the expense before saving.")
                }
            }
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "The proposed action failed validation.", needsClarification = true)
        }
    }

    fun modelPrompt(userText: String): String =
        "Classify this untrusted request using only Dhanam finance actions. Return exactly one JSON object with keys intent, title, amount, category, date, message. Allowed intent values: expense, income, budget, clarify. Expense categories: Food, Transport, Shopping, Bills, Health, Education, Other. Income categories: Salary, Business, Freelance, Investment, Gift, Other. Use clarify when required information is missing. Never infer an amount, never claim a write occurred, and refuse unrelated generic tasks. Request: " + userText.take(1000)

    /** Applies explicit corrections to a staged draft; this function never writes to storage. */
    fun applyCorrection(action: PlannedFinanceAction, text: String): PlannerResult? {
        val input = text.trim()
        val lower = input.lowercase()
        val titleMatch = Regex("""(?i)(?:description|title|item)\s+to\s+(.+)$""").find(input)
        val categoryWord = categories.keys.sortedByDescending { it.length }
            .firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lower) }
        val amountMatch = amountPattern.findAll(input).lastOrNull()
        val amountIntent = amountMatch != null && (
            lower.contains("to ${amountMatch.groupValues[1].lowercase()}") ||
                lower.contains("amount") || lower.contains("cost") || lower.contains("spent") ||
                lower.contains("actually") || lower.contains("change") || lower.contains("correct")
            )
        return try {
            when (action) {
                is PlannedFinanceAction.AddExpense -> {
                    val category = categoryWord?.let { categories[it] } ?: action.category
                    when {
                        titleMatch != null -> {
                            val title = titleMatch.groupValues[1].trim().trimEnd('.', '!', '?').take(120)
                            val validated = ExpenseInputValidator.validate(title, MoneyParser.formatPaise(action.amountPaise).removePrefix("₹").replace(",", ""), category, action.date.toString(), action.note)
                            PlannerResult(action.copy(title = validated.title, category = validated.category), "Updated the draft. Review it before saving.")
                        }
                        amountIntent && amountMatch != null -> {
                            val validated = ExpenseInputValidator.validate(action.title, amountMatch.groupValues[1], category, action.date.toString(), action.note)
                            PlannerResult(action.copy(amountPaise = validated.amountPaise, category = validated.category), "Updated the amount. Review the draft before saving.")
                        }
                        categoryWord != null && (lower.contains("category") || lower.contains("change") || lower.contains("actually")) ->
                            PlannerResult(action.copy(category = category), "Updated the category. Review the draft before saving.")
                        else -> null
                    }
                }
                is PlannedFinanceAction.AddIncome -> {
                    val incomeCategory = IncomeInputValidator.categories.firstOrNull {
                        Regex("""\b${Regex.escape(it.lowercase())}\b""").containsMatchIn(lower)
                    } ?: action.category
                    when {
                        titleMatch != null -> {
                            val title = titleMatch.groupValues[1].trim().trimEnd('.', '!', '?').take(80)
                            val validated = IncomeInputValidator.validate(
                                title, BigDecimal.valueOf(action.amountPaise, 2).toPlainString(),
                                incomeCategory, action.date.toString(), action.note
                            )
                            PlannerResult(action.copy(title = validated.title, category = validated.category),
                                "Updated the income description. Review it before saving.")
                        }
                        amountIntent && amountMatch != null -> {
                            val validated = IncomeInputValidator.validate(
                                action.title, amountMatch.groupValues[1], incomeCategory,
                                action.date.toString(), action.note
                            )
                            PlannerResult(action.copy(amountPaise = validated.amountPaise, category = validated.category),
                                "Updated the income amount. Review it before saving.")
                        }
                        incomeCategory != action.category && (lower.contains("category") || lower.contains("change") || lower.contains("actually")) ->
                            PlannerResult(action.copy(category = incomeCategory), "Updated the income category. Review it before saving.")
                        else -> null
                    }
                }
                is PlannedFinanceAction.SetBudget -> when {
                    amountIntent && amountMatch != null -> PlannerResult(
                        action.copy(amountPaise = MoneyParser.parseRupeesToPaise(amountMatch.groupValues[1]), category = categoryWord?.let { categories[it] } ?: action.category),
                        "Updated the budget draft. Review it before saving."
                    )
                    categoryWord != null && (lower.contains("category") || lower.contains("change") || lower.contains("actually")) ->
                        PlannerResult(action.copy(category = categories.getValue(categoryWord)), "Updated the budget category. Review it before saving.")
                    else -> null
                }
                is PlannedFinanceAction.AddLoan -> {
                    val personMatch = Regex("""(?i)(?:to|from|person)\s+(.+)$""").find(input)
                    when {
                        amountIntent && amountMatch != null -> {
                            val validated = KhataValidator.validate(
                                action.personName, action.direction, amountMatch.groupValues[1],
                                action.openedOn.toString(), action.dueOn?.toString().orEmpty(),
                                action.note, action.phoneNumber
                            )
                            PlannerResult(action.copy(principalPaise = validated.principalPaise), "Updated the Khata amount. Review it before saving.")
                        }
                        personMatch != null -> {
                            val validated = KhataValidator.validate(
                                personMatch.groupValues[1].trim().trimEnd('.', '!', '?'),
                                action.direction,
                                java.math.BigDecimal.valueOf(action.principalPaise, 2).toPlainString(),
                                action.openedOn.toString(), action.dueOn?.toString().orEmpty(),
                                action.note, action.phoneNumber
                            )
                            PlannerResult(action.copy(personName = validated.personName), "Updated the Khata person. Review it before saving.")
                        }
                        else -> null
                    }
                }
                is PlannedFinanceAction.RecordRepayment -> if (amountIntent && amountMatch != null) {
                    PlannerResult(
                        action.copy(amountPaise = MoneyParser.parseRupeesToPaise(amountMatch.groupValues[1]), requestId = java.util.UUID.randomUUID().toString()),
                        "Updated the repayment amount. Review it before saving."
                    )
                } else null
            }
        } catch (error: Exception) {
            PlannerResult(message = error.message ?: "That correction is invalid.", needsClarification = true)
        }
    }

}
