package com.aistudio.dhanam.expense.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.data.RepaymentEntity
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.domain.MoneyParser
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate

data class AgentChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val text: String)
data class AgentState(
    val messages: List<AgentChatMessage> = listOf(AgentChatMessage(role = "assistant", text = "Hi, I'm Dhanam. I can prepare an expense or budget change for your review. I will never save a financial change without your confirmation.")),
    val pendingAction: PlannedFinanceAction? = null,
    val busy: Boolean = false,
    val activity: String = "idle",
    val status: String? = null
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FinanceDatabase.getInstance(application)
    private val dao = db.financeDao()
    private var localInference: LocalInferenceViewModel? = null
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    fun attachLocalInference(viewModel: LocalInferenceViewModel) {
        localInference = viewModel
    }


    fun submit(text: String) {
        val input = text.trim().take(1000)
        if (input.isEmpty() || _state.value.busy) return
        val previousPending = _state.value.pendingAction
        _state.value = _state.value.copy(
            messages = _state.value.messages + AgentChatMessage(role = "user", text = input),
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

        val repayment = Regex("""(?i)^record repayment\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+from\s+(.+)$""").matchEntire(input)
        if (repayment != null) {
            _state.value = _state.value.copy(busy = true)
            viewModelScope.launch {
                try {
                    val amountPaise = MoneyParser.parseRupeesToPaise(repayment.groupValues[1])
                    val person = repayment.groupValues[2].trim()
                    val matches = dao.observeLoans().first().filter {
                        it.personName.equals(person, ignoreCase = true) &&
                            it.direction == KhataValidator.LENT && it.outstandingPaise > 0
                    }
                    if (matches.size != 1) {
                        appendAssistant(if (matches.isEmpty()) "I couldn't find an open loan for “$person” that you lent them. Check the name and Khata records; no change was made." else "More than one open Khata record matches “$person”. Open Khata and resolve the exact record first; no change was made.")
                        _state.value = _state.value.copy(activity = "idle", busy = false)
                    } else {
                        val loan = matches.single()
                        require(amountPaise <= loan.outstandingPaise) { "Repayment exceeds the outstanding amount of ${MoneyParser.formatPaise(loan.outstandingPaise)}." }
                        val action = PlannedFinanceAction.RecordRepayment(person, amountPaise, loan.id, LocalDate.now(), UUID.randomUUID().toString())
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

        val result = AgentActionPlanner.parse(input)
        if (result.action != null || !result.needsClarification) {
            _state.value = _state.value.copy(
                messages = _state.value.messages + AgentChatMessage(role = "assistant", text = result.message),
                pendingAction = result.action,
                activity = if (result.action != null) "awaiting_confirmation" else "idle"
            )
            return
        }

        val inference = localInference
        if (inference?.state?.value?.isReady == true) {
            _state.value = _state.value.copy(busy = true)
            viewModelScope.launch {
                try {
                    val proposalText = inference.generateForAgent(AgentActionPlanner.modelPrompt(input))
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
            _state.value = _state.value.copy(
                messages = _state.value.messages + AgentChatMessage(role = "assistant", text = result.message + " For more flexible phrasing, load the verified on-device model in Settings; without it I use only the built-in command patterns."),
                pendingAction = null,
                activity = "idle"
            )
        }
    }

    fun cancelPending() {
        _state.value = _state.value.copy(pendingAction = null, activity = "idle", status = "No changes were saved.")
        appendAssistant("Cancelled. Nothing was changed.")
    }

    fun confirmPending() {
        val action = _state.value.pendingAction ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, pendingAction = null, activity = "acting", status = "Validating and saving…")
        viewModelScope.launch {
            try {
                when (action) {
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
                        appendAssistant("Verified: expense “${saved.title}” saved as record #${saved.id} for ${MoneyParser.formatPaise(saved.amountPaise)}.")
                    }
                    is PlannedFinanceAction.SetBudget -> {
                        val monthEpochDay = action.month.atDay(1).toEpochDay()
                        dao.saveBudget(action.category, monthEpochDay, action.amountPaise, System.currentTimeMillis())
                        val saved = dao.findBudget(action.category, monthEpochDay)
                        check(saved != null && saved.amountPaise == action.amountPaise) {
                            "Budget save could not be verified. Check Insights before retrying."
                        }
                        appendAssistant("Verified: ${saved.category} budget set to ${MoneyParser.formatPaise(saved.amountPaise)} for ${action.month}.")
                    }
                    is PlannedFinanceAction.RecordRepayment -> {
                        val loan = dao.findLoan(action.loanId) ?: error("The Khata record no longer exists.")
                        check(loan.personName.equals(action.personName, ignoreCase = true) && loan.outstandingPaise >= action.amountPaise && !loan.isSettled) {
                            "The Khata record changed or no longer has enough outstanding balance. Review it before retrying."
                        }
                        val inserted = dao.recordRepayment(RepaymentEntity(
                            loanId = action.loanId, amountPaise = action.amountPaise,
                            paidOnEpochDay = action.date.toEpochDay(), note = "Recorded by Dhanam assistant",
                            requestId = action.requestId, createdAtEpochMillis = System.currentTimeMillis()
                        ))
                        check(inserted) { "This repayment request was already recorded; check Khata before retrying." }
                        val savedLoan = dao.findLoan(action.loanId)
                        val savedRepayment = dao.findRepaymentByRequestId(action.requestId)
                        check(savedLoan != null && savedRepayment != null && savedLoan.paidPaise >= loan.paidPaise + action.amountPaise) {
                            "Repayment save could not be verified. Check Khata before retrying."
                        }
                        appendAssistant("Verified: repayment of ${MoneyParser.formatPaise(action.amountPaise)} recorded for ${action.personName}.")
                    }
                }
                _state.value = _state.value.copy(pendingAction = null, activity = "complete", status = "Action verified against the local database.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(activity = "error", status = e.message ?: "Action failed. No success is claimed.")
                appendAssistant(e.message ?: "The action failed. Check the record before retrying.")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun dismissStatus() { _state.value = _state.value.copy(status = null, activity = if (_state.value.pendingAction != null) "awaiting_confirmation" else "idle") }

    private fun appendAssistant(text: String) {
        _state.value = _state.value.copy(messages = _state.value.messages + AgentChatMessage(role = "assistant", text = text))
    }
}
