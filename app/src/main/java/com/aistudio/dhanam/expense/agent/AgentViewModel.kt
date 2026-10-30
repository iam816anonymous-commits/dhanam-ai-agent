package com.aistudio.dhanam.expense.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.data.KhataLoanEntity
import com.aistudio.dhanam.expense.data.AgentSkillEntity
import com.aistudio.dhanam.expense.data.RepaymentEntity
import com.aistudio.dhanam.expense.data.IncomeEntity
import com.aistudio.dhanam.expense.data.BudgetEntity
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.domain.IncomeInputValidator
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class AgentChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val text: String)
data class AgentState(
    val messages: List<AgentChatMessage> = listOf(AgentChatMessage(role = "assistant", text = "Hi, I'm Dhanam. I can prepare expenses, income, budgets, lending, borrowing and repayments for your review. I will never save a financial change without your confirmation.")),
    val pendingAction: PlannedFinanceAction? = null,
    val busy: Boolean = false,
    val activity: String = "idle",
    val status: String? = null,
    val undoDescription: String? = null
)

private sealed class AgentUndoAction(val description: String) {
    data class Expense(val id: Long, val updatedAt: Long, val title: String) : AgentUndoAction("Expense: $title")
    data class Income(val id: Long, val updatedAt: Long, val title: String) : AgentUndoAction("Income: $title")
    data class Loan(val id: Long, val updatedAt: Long, val person: String) : AgentUndoAction("Khata record: $person")
    data class Budget(val category: String, val monthEpochDay: Long, val appliedUpdatedAt: Long, val previous: BudgetEntity?) : AgentUndoAction("Budget: $category")
    data class Repayment(val requestId: String, val person: String) : AgentUndoAction("Repayment: $person")
}

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FinanceDatabase.getInstance(application)
    private val dao = db.financeDao()
    private var localInference: LocalInferenceViewModel? = null
    private var sharedCoordinator: SharedAgentCoordinator? = null
    private var lastUndoAction: AgentUndoAction? = null
    @Volatile private var activeSkills: List<AgentSkillEntity> = emptyList()
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAgentSkills().collect { skills ->
                activeSkills = skills.filter { it.status == "ACTIVE" }
            }
        }
    }

    fun attachLocalInference(viewModel: LocalInferenceViewModel) {
        localInference = viewModel
        sharedCoordinator = viewModel.sharedAgentCoordinator
    }


    fun submit(text: String) {
        val input = text.trim().take(1000)
        if (input.isEmpty() || _state.value.busy) return
        val previousPending = _state.value.pendingAction
        _state.value = _state.value.copy(
            messages = (_state.value.messages + AgentChatMessage(role = "user", text = input)).takeLast(MAX_CHAT_MESSAGES - 1),
            status = null, activity = "thinking"
        )

        if (input.lowercase() in setOf("cancel", "cancel that", "never mind", "nevermind", "stop", "discard")) {
            _state.value = _state.value.copy(pendingAction = null, activity = "idle", status = "No changes were saved.")
            appendAssistant("Cancelled. Nothing was changed.")
            return
        }

        if (previousPending != null) {
            val correction = AgentActionPlanner.applyCorrection(previousPending, input)
            if (correction != null) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + AgentChatMessage(role = "assistant", text = correction.message),
                    pendingAction = correction.action ?: previousPending,
                    activity = if (correction.action != null || correction.needsClarification) "awaiting_confirmation" else "idle"
                )
                return
            }
            if (Regex("""(?i)^(actually|change that|correct that|instead|make it|set it|change the amount|change the category|change the description|change description|change title|change item)\b""").containsMatchIn(input)) {
                appendAssistant("I couldn't apply that correction safely. Please say, for example, “actually change that to 300”, “change category to transport”, or “change description to taxi”. The current draft has not been saved.")
                _state.value = _state.value.copy(pendingAction = previousPending, activity = "awaiting_confirmation")
                return
            }
        }

        // A new command supersedes the previous draft. Clear it before asynchronous lookup
        // so the old confirmation cannot be tapped while the new request is being resolved.
        _state.value = _state.value.copy(pendingAction = null)

        val repayment = Regex("""(?i)^record repayment\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+(from|to)\s+(.+)$""").matchEntire(input)
        if (repayment != null) {
            _state.value = _state.value.copy(busy = true)
            viewModelScope.launch {
                try {
                    val amountPaise = MoneyParser.parseRupeesToPaise(repayment.groupValues[1])
                    val repaymentDirection = if (repayment.groupValues[2].equals("from", ignoreCase = true)) {
                        KhataValidator.LENT
                    } else {
                        KhataValidator.BORROWED
                    }
                    val person = repayment.groupValues[3].trim()
                    val matches = dao.observeLoans().first().filter {
                        it.personName.equals(person, ignoreCase = true) &&
                            it.direction == repaymentDirection && it.outstandingPaise > 0
                    }
                    if (matches.size != 1) {
                        val relation = if (repaymentDirection == KhataValidator.LENT) "you lent them" else "you borrowed from them"
                        appendAssistant(if (matches.isEmpty()) "I couldn't find an open loan for “$person” that $relation. Check the name and Khata records; no change was made." else "More than one open Khata record matches “$person”. Open Khata and resolve the exact record first; no change was made.")
                        _state.value = _state.value.copy(activity = "idle", busy = false)
                    } else {
                        val loan = matches.single()
                        require(amountPaise <= loan.outstandingPaise) { "Repayment exceeds the outstanding amount of ${MoneyParser.formatPaise(loan.outstandingPaise)}." }
                        val action = PlannedFinanceAction.RecordRepayment(person, amountPaise, loan.id, LocalDate.now(), UUID.randomUUID().toString(), repaymentDirection)
                        _state.value = _state.value.copy(
                            messages = _state.value.messages + AgentChatMessage(role = "assistant", text = "I found one matching open Khata record. Review it below; nothing is saved yet."),
                            pendingAction = action, activity = "awaiting_confirmation", busy = false
                        )
                    }
                } catch (e: Exception) {
                    appendAssistant(e.message ?: "I couldn't prepare that repayment.")
                    _state.value = _state.value.copy(activity = "idle", busy = false)
                }
            }
            return
        }

        val result = AgentActionPlanner.parseWithLearnedSkills(input, activeSkills)
        val analysisAction = result.action as? PlannedFinanceAction.AnalyzeSpending
        if (analysisAction != null) {
            _state.value = _state.value.copy(
                messages = _state.value.messages + AgentChatMessage(role = "assistant", text = result.message),
                pendingAction = null,
                busy = true,
                activity = "analyzing"
            )
            viewModelScope.launch {
                try {
                    appendAssistant(buildSpendingAnalysis(analysisAction.month, analysisAction.category))
                    _state.value = _state.value.copy(activity = "complete", status = "Analysis calculated from local financial records.")
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    appendAssistant(error.message ?: "I couldn't complete the spending analysis.")
                    _state.value = _state.value.copy(activity = "error", status = "Analysis could not be completed.")
                } finally {
                    _state.value = _state.value.copy(busy = false)
                }
            }
            return
        }
        if (result.action != null || !result.needsClarification) {
            _state.value = _state.value.copy(
                messages = _state.value.messages + AgentChatMessage(role = "assistant", text = result.message),
                pendingAction = result.action,
                activity = if (result.action != null) "awaiting_confirmation" else "idle"
            )
            return
        }

        val inference = localInference
        // Foreground requests are user work, not background learning: a loaded model must be usable 24/7.
        if (inference?.state?.value?.isReady == true) {
            _state.value = _state.value.copy(busy = true)
            viewModelScope.launch {
                try {
                    val coordinator = checkNotNull(sharedCoordinator) {
                        "The shared financial model coordinator is not attached."
                    }
                    val proposalText = coordinator.run(
                        AgentTaskRequest(
                            role = DhanamAgentRole.FINANCE_OPERATIONS,
                            prompt = AgentActionPlanner.modelPrompt(input),
                            priority = AgentTaskPriority.USER_REQUEST,
                            requiresNightWindow = false
                        )
                    ).output
                    val proposal = AgentActionPlanner.parseModelProposal(proposalText)
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + AgentChatMessage(role = "assistant", text = proposal.message),
                        pendingAction = proposal.action,
                        activity = if (proposal.action != null) "awaiting_confirmation" else "idle",
                        busy = false
                    )
                } catch (error: Exception) {
                    appendAssistant("I couldn't safely interpret that with the local model. No changes were made. Try a specific command or clarify the amount/category. ${error.message ?: ""}".trim())
                    _state.value = _state.value.copy(pendingAction = null, activity = "idle", busy = false)
                }
            }
        } else {
            val modelAvailabilityMessage =
                "Load and activate a compatible verified on-device model in Settings for flexible phrasing. Built-in financial commands and local spending/Khata analysis work without it."
            _state.value = _state.value.copy(
                messages = _state.value.messages + AgentChatMessage(role = "assistant", text = result.message + " " + modelAvailabilityMessage),
                pendingAction = null,
                activity = "idle"
            )
        }
    }

    fun cancelPending() {
        _state.value = _state.value.copy(pendingAction = null, activity = "idle", status = "No changes were saved.")
        appendAssistant("Cancelled. Nothing was changed.")
    }

    /** Reverses only the latest assistant-created record and only if it is unchanged. */
    fun undoLastAction() {
        val action = lastUndoAction ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, activity = "acting", status = "Checking whether the last action can be safely reversed…")
        viewModelScope.launch {
            try {
                val undone = when (action) {
                    is AgentUndoAction.Expense -> dao.deleteExpenseIfUnchanged(action.id, action.updatedAt) == 1
                    is AgentUndoAction.Income -> dao.deleteIncomeIfUnchanged(action.id, action.updatedAt) == 1
                    is AgentUndoAction.Loan -> dao.deleteLoanIfUnchanged(action.id, action.updatedAt) == 1
                    is AgentUndoAction.Budget -> {
                        val current = dao.findBudget(action.category, action.monthEpochDay)
                        if (current == null || current.updatedAtEpochMillis != action.appliedUpdatedAt) false
                        else if (action.previous == null) dao.deleteBudgetById(current.id) == 1
                        else dao.updateBudget(action.previous.copy(updatedAtEpochMillis = System.currentTimeMillis())) == 1
                    }
                    is AgentUndoAction.Repayment -> dao.undoRepayment(action.requestId)
                }
                check(undone) { "Undo was not applied because the record changed or no longer exists. No unrelated record was modified." }
                lastUndoAction = null
                _state.value = _state.value.copy(activity = "complete", status = "The last assistant action was reversed and checked against the local database.", undoDescription = null)
                appendAssistant("Undid ${action.description}. The local record was checked after the reversal.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(activity = "error", status = error.message ?: "The last action could not be safely reversed.")
                appendAssistant(error.message ?: "Undo failed. Check the financial record before trying again.")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun confirmPending() {
        val action = _state.value.pendingAction ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, pendingAction = null, activity = "acting", status = "Validating and saving…")
        viewModelScope.launch {
            try {
                when (action) {
                    is PlannedFinanceAction.AnalyzeSpending -> error("Read-only analysis cannot be saved as a financial transaction.")
                    is PlannedFinanceAction.AddExpense -> {
                        val now = System.currentTimeMillis()
                        val id = dao.insertExpense(ExpenseEntity(
                            title = action.title, amountPaise = action.amountPaise, category = action.category,
                            occurredOnEpochDay = action.date.toEpochDay(), note = action.note,
                            createdAtEpochMillis = now, updatedAtEpochMillis = now
                        ))
                        val saved = dao.findExpense(id)
                        check(saved != null && saved.amountPaise == action.amountPaise && saved.title == action.title) {
                            "Expense save could not be verified. Check the Expenses list before retrying."
                        }
                        lastUndoAction = AgentUndoAction.Expense(saved.id, saved.updatedAtEpochMillis, saved.title)
                        appendAssistant("Verified: expense “${saved.title}” saved as record #${saved.id} for ${MoneyParser.formatPaise(saved.amountPaise)}.")
                    }
                    is PlannedFinanceAction.AddIncome -> {
                        val validated = IncomeInputValidator.validate(
                            action.title,
                            BigDecimal.valueOf(action.amountPaise, 2).toPlainString(),
                            action.category,
                            action.date.toString(),
                            action.note
                        )
                        val now = System.currentTimeMillis()
                        val id = dao.insertIncome(
                            IncomeEntity(
                                title = validated.title,
                                amountPaise = validated.amountPaise,
                                category = validated.category,
                                occurredOnEpochDay = validated.occurredOn.toEpochDay(),
                                note = validated.note,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now
                            )
                        )
                        val saved = dao.findIncome(id)
                        check(saved != null && saved.title == validated.title &&
                            saved.amountPaise == validated.amountPaise &&
                            saved.category == validated.category) {
                            "Income save could not be verified. Check Expenses → Income before retrying."
                        }
                        lastUndoAction = AgentUndoAction.Income(saved.id, saved.updatedAtEpochMillis, saved.title)
                        appendAssistant("Verified: income “${saved.title}” saved as record #${saved.id} for ${MoneyParser.formatPaise(saved.amountPaise)}.")
                    }
                    is PlannedFinanceAction.AddLoan -> {
                        val validated = KhataValidator.validate(
                            action.personName,
                            action.direction,
                            BigDecimal.valueOf(action.principalPaise, 2).toPlainString(),
                            action.openedOn.toString(),
                            action.dueOn?.toString().orEmpty(),
                            action.note,
                            action.phoneNumber
                        )
                        val now = System.currentTimeMillis()
                        val id = dao.insertLoan(
                            KhataLoanEntity(
                                personName = validated.personName,
                                direction = validated.direction,
                                principalPaise = validated.principalPaise,
                                paidPaise = 0L,
                                openedOnEpochDay = validated.openedOn.toEpochDay(),
                                dueOnEpochDay = validated.dueOn?.toEpochDay(),
                                note = validated.note,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                                phoneNumber = validated.phoneNumber
                            )
                        )
                        val saved = dao.findLoan(id)
                        check(saved != null && saved.personName == validated.personName &&
                            saved.direction == validated.direction && saved.principalPaise == validated.principalPaise &&
                            saved.outstandingPaise == validated.principalPaise) {
                            "Khata save could not be verified. Check Khata before retrying."
                        }
                        lastUndoAction = AgentUndoAction.Loan(saved.id, saved.updatedAtEpochMillis, saved.personName)
                        val relation = if (saved.direction == KhataValidator.LENT) "lent to" else "borrowed from"
                        appendAssistant("Verified: recorded ${MoneyParser.formatPaise(saved.principalPaise)} $relation ${saved.personName} in Khata.")
                    }
                    is PlannedFinanceAction.SetBudget -> {
                        val monthEpochDay = action.month.atDay(1).toEpochDay()
                        val previous = dao.findBudget(action.category, monthEpochDay)
                        val now = System.currentTimeMillis()
                        dao.saveBudget(action.category, monthEpochDay, action.amountPaise, now)
                        val saved = dao.findBudget(action.category, monthEpochDay)
                        check(saved != null && saved.amountPaise == action.amountPaise) {
                            "Budget save could not be verified. Check Insights before retrying."
                        }
                        lastUndoAction = AgentUndoAction.Budget(action.category, monthEpochDay, saved.updatedAtEpochMillis, previous)
                        appendAssistant("Verified: ${saved.category} budget set to ${MoneyParser.formatPaise(saved.amountPaise)} for ${action.month}.")
                    }
                    is PlannedFinanceAction.RecordRepayment -> {
                        val loan = dao.findLoan(action.loanId) ?: error("The Khata record no longer exists.")
                        check(loan.personName.equals(action.personName, ignoreCase = true) &&
                            loan.direction == action.direction && loan.outstandingPaise >= action.amountPaise && !loan.isSettled) {
                            "The Khata record changed or no longer has enough outstanding balance. Review it before retrying."
                        }
                        val repaymentNow = System.currentTimeMillis()
                        val inserted = dao.recordRepayment(RepaymentEntity(
                            loanId = action.loanId, amountPaise = action.amountPaise,
                            paidOnEpochDay = action.date.toEpochDay(), note = "Recorded by Dhanam assistant",
                            requestId = action.requestId, createdAtEpochMillis = repaymentNow
                        ))
                        check(inserted) { "This repayment request was already recorded; check Khata before retrying." }
                        val savedLoan = dao.findLoan(action.loanId)
                        val savedRepayment = dao.findRepaymentByRequestId(action.requestId)
                        check(savedLoan != null && savedRepayment != null && savedLoan.paidPaise >= loan.paidPaise + action.amountPaise) {
                            "Repayment save could not be verified. Check Khata before retrying."
                        }
                        lastUndoAction = AgentUndoAction.Repayment(action.requestId, action.personName)
                        appendAssistant("Verified: repayment of ${MoneyParser.formatPaise(action.amountPaise)} recorded for ${action.personName}.")
                    }
                }
                _state.value = _state.value.copy(pendingAction = null, activity = "complete", status = "Action verified against the local database.", undoDescription = lastUndoAction?.description)
            } catch (e: Exception) {
                _state.value = _state.value.copy(activity = "error", status = e.message ?: "Action failed. No success is claimed.")
                appendAssistant(e.message ?: "The action failed. Check the record before retrying.")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private suspend fun buildSpendingAnalysis(month: YearMonth, category: String?): String {
        val today = if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
        val expenses = dao.expensesSince(today.minusDays(365).toEpochDay())
        val budgets = dao.observeBudgetsForMonth(month.atDay(1).toEpochDay()).first()
        val loans = dao.observeLoans().first()
        val incomes = dao.incomeSince(month.atDay(1).toEpochDay())
            .filter { it.occurredOnEpochDay <= today.toEpochDay() }
        val snapshot = FinancialAnalysisEngine.analyze(expenses, budgets, loans, today)
        val monthStart = month.atDay(1).toEpochDay()
        val monthEnd = today.toEpochDay()
        val monthExpenses = expenses.filter {
            it.occurredOnEpochDay in monthStart..monthEnd && it.amountPaise > 0 &&
                (category == null || it.category.equals(category, ignoreCase = true))
        }
        val selectedExpenseTotalPaise = monthExpenses.fold(0L) { total, expense -> Math.addExact(total, expense.amountPaise) }
        val incomeTotalPaise = incomes.fold(0L) { total, record -> Math.addExact(total, record.amountPaise) }
        val lines = mutableListOf<String>()
        lines += if (category == null) "Spending review for $month" else "$category spending review for $month"
        val spendingLabel = if (category == null) "Recorded expenses" else "Recorded $category expenses"
        lines += "$spendingLabel: ${MoneyParser.formatPaise(selectedExpenseTotalPaise)} across ${monthExpenses.size} transaction(s)."
        if (incomes.isEmpty()) {
            lines += "Income: no records entered for this month. Net cash flow is unknown, not zero."
        } else {
            lines += "Recorded income: ${MoneyParser.formatPaise(incomeTotalPaise)} across ${incomes.size} record(s)."
            lines += "Overall net recorded cash flow: ${MoneyParser.formatPaise(Math.subtractExact(incomeTotalPaise, snapshot.totalSpentPaise))}. This is not a bank balance."
        }
        val relevantMovements = snapshot.categoryMovements.filter {
            category == null || it.category.equals(category, ignoreCase = true)
        }
        if (relevantMovements.isNotEmpty()) {
            lines += "Category movement versus the previous month:"
            relevantMovements.take(4).forEach { movement ->
                val change = if (movement.changePaise >= 0) "increased by " else "decreased by "
                lines += "• ${movement.category}: ${MoneyParser.formatPaise(movement.currentMonthPaise)} this month, ${MoneyParser.formatPaise(movement.previousMonthPaise)} previously; $change${MoneyParser.formatPaise(kotlin.math.abs(movement.changePaise))}."
            }
        }
        val relevantOverruns = snapshot.budgetOverruns.filterKeys {
            category == null || it.equals(category, ignoreCase = true)
        }
        if (relevantOverruns.isNotEmpty()) {
            lines += "Budget overruns:"
            relevantOverruns.toList().sortedByDescending { it.second }.take(4).forEach { (category, overrun) ->
                lines += "• $category is over its recorded budget by ${MoneyParser.formatPaise(overrun)}."
            }
        }
        val relevantOpportunities = snapshot.opportunities.filter { opportunity ->
            category == null || opportunity.title.contains(category, ignoreCase = true) ||
                opportunity.evidence.contains(category, ignoreCase = true) ||
                opportunity.suggestedAction.contains(category, ignoreCase = true)
        }
        if (relevantOpportunities.isNotEmpty()) {
            lines += "Potential actions to review:"
            relevantOpportunities.take(5).forEach { opportunity ->
                lines += "• ${opportunity.title}: ${opportunity.suggestedAction} Evidence: ${opportunity.evidence} Confidence: ${opportunity.confidence.name.lowercase()}. No guaranteed saving amount is assumed."
            }
        } else {
            lines += if (category == null) {
                "No specific spending anomaly was identified in the available records. This does not prove that all spending is necessary."
            } else {
                "No specific $category spending anomaly was identified in the available records. This does not prove that all spending is necessary."
            }
        }
        lines += "Khata outstanding: lent ${MoneyParser.formatPaise(snapshot.lentOutstandingPaise)}; borrowed ${MoneyParser.formatPaise(snapshot.borrowedOutstandingPaise)}."
        val openLoans = loans.filter { it.outstandingPaise > 0 }.sortedByDescending { it.outstandingPaise }
        if (openLoans.isEmpty()) {
            lines += "No open lending or borrowing records."
        } else {
            lines += "Open Khata records:"
            openLoans.take(5).forEach { loan ->
                val relation = if (loan.direction == KhataValidator.LENT) "You lent to" else "You borrowed from"
                val due = loan.dueOnEpochDay?.let { " · due ${LocalDate.ofEpochDay(it)}" }.orEmpty()
                lines += "• $relation ${loan.personName}: ${MoneyParser.formatPaise(loan.outstandingPaise)} outstanding$due."
            }
        }
        lines += "Recorded expense history coverage: ${snapshot.dataCoverageDays} day(s). Recommendations are less reliable when history is sparse."
        return lines.joinToString("\n")
    }

    fun clearChat() {
        if (_state.value.busy) return
        _state.value = AgentState(
            messages = listOf(
                AgentChatMessage(
                    role = "assistant",
                    text = "Hi, I'm Dhanam. I can prepare expenses, income, budgets, lending, borrowing and repayments. I'll show a preview and ask before saving."
                )
            ),
            pendingAction = null,
            busy = false,
            activity = "idle",
            status = "Conversation cleared. Financial records were not changed."
        )
    }

    fun dismissStatus() { _state.value = _state.value.copy(status = null, activity = if (_state.value.pendingAction != null) "awaiting_confirmation" else "idle") }

    private fun appendAssistant(text: String) {
        val messages = (_state.value.messages + AgentChatMessage(role = "assistant", text = text))
            .takeLast(MAX_CHAT_MESSAGES)
        _state.value = _state.value.copy(messages = messages)
    }

    private companion object {
        const val MAX_CHAT_MESSAGES = 100
    }
}
