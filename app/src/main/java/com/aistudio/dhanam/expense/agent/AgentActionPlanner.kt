package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.time.LocalDate
import java.time.YearMonth

sealed interface PlannedFinanceAction {
    val summary: String
    data class AddExpense(val title: String, val amountPaise: Long, val category: String, val date: LocalDate, val note: String = "") : PlannedFinanceAction {
        override val summary = "Record expense: $title · ${MoneyParser.formatPaise(amountPaise)} · $category · $date"
    }
    data class SetBudget(val category: String, val amountPaise: Long, val month: YearMonth) : PlannedFinanceAction {
        override val summary = "Set ${category} budget to ${MoneyParser.formatPaise(amountPaise)} for ${month}"
    }
    data class RecordRepayment(val personName: String, val amountPaise: Long, val loanId: Long, val date: LocalDate, val requestId: String) : PlannedFinanceAction {
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

    fun parse(text: String): PlannerResult {
        val input = text.trim().replace(Regex("\s+"), " ")
        if (input.isEmpty()) return PlannerResult(message = "Tell me what you want to do.")
        val lower = input.lowercase()
        if (lower in setOf("help", "what can you do", "commands")) {
            return PlannerResult(message = "Try: “I spent 250 on lunch”, “set food budget to 3000”, or “record repayment 500 from Ravi”. I'll show a preview and ask before saving.")
        }
        if (lower.contains("budget")) {
            val amount = amountPattern.findAll(input).lastOrNull()?.groupValues?.get(1)
                ?: return PlannerResult(message = "What amount should the budget be?", needsClarification = true)
            val categoryWord = categories.keys.sortedByDescending { it.length }.firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lower) }
                ?: return PlannerResult(message = "Which category is this budget for? Choose Food, Transport, Shopping, Bills, Health, Education or Other.", needsClarification = true)
            val category = categories[categoryWord] ?: categoryWord.replaceFirstChar { it.uppercase() }
            return try {
                val paise = MoneyParser.parseRupeesToPaise(amount)
                PlannerResult(PlannedFinanceAction.SetBudget(category, paise, YearMonth.now()), "Review this budget change before saving.")
            } catch (e: Exception) { PlannerResult(message = e.message ?: "Check the budget amount.", needsClarification = true) }
        }
        if (lower.contains("repayment") || lower.contains("paid") || lower.contains("pay back") || lower.contains("paid back")) {
            return PlannerResult(message = "I need to match the repayment to exactly one open Khata record before I can prepare it. Use “record repayment 500 from Ravi” and confirm the matching person in the preview.", needsClarification = true)
        }
        val amountMatch = amountPattern.find(input)
            ?: return PlannerResult(message = "I couldn't find an amount. Try “I spent 250 on lunch”.", needsClarification = true)
        if (!(lower.contains("spent") || lower.contains("expense") || lower.contains("bought") || lower.contains("paid for"))) {
            return PlannerResult(message = "I can prepare an expense or budget change. Try “I spent 250 on lunch”.", needsClarification = true)
        }
        val categoryWord = categories.keys.sortedByDescending { it.length }.firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lower) }
        val category = categoryWord?.let { categories[it] } ?: "Other"
        val title = when {
            Regex("""\bon\s+(.+)$""", RegexOption.IGNORE_CASE).containsMatchIn(input) ->
                Regex("""\bon\s+(.+)$""", RegexOption.IGNORE_CASE).find(input)!!.groupValues[1]
                    .replace(Regex("""[.!?]$"""), "").take(120)
            else -> input.replace(amountMatch.value, "").replace(Regex("""(?i)\b(i spent|spent|expense|add|record|bought|paid for)\b"""), "").trim().take(120).ifBlank { category }
        }
        return try {
            val validated = ExpenseInputValidator.validate(title, amountMatch.groupValues[1], category, LocalDate.now().toString(), "")
            PlannerResult(PlannedFinanceAction.AddExpense(validated.title, validated.amountPaise, validated.category, validated.occurredOn), "Review the expense before saving.")
        } catch (e: Exception) {
            PlannerResult(message = e.message ?: "I couldn't validate that expense.", needsClarification = true)
        }
    }
}
