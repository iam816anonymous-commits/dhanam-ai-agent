package com.aistudio.dhanam.expense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.RoomExpenseRepository
import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomExpenseRepository(FinanceDatabase.getInstance(application).expenseDao())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }

    fun saveExpense(id: Long?, title: String, amount: String, category: String, date: String, note: String) {
        viewModelScope.launch {
            try {
                val input = ExpenseInputValidator.validate(title, amount, category, date, note)
                val now = System.currentTimeMillis()
                val existing = id?.let { repository.findById(it) }
                if (id != null && existing == null) {
                    error("This expense no longer exists. Refresh the list and try again.")
                }
                val entity = ExpenseEntity(
                    id = existing?.id ?: 0,
                    title = input.title,
                    amountPaise = input.amountPaise,
                    category = input.category,
                    occurredOnEpochDay = input.occurredOn.toEpochDay(),
                    note = input.note,
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now
                )
                if (existing == null) repository.add(entity) else repository.update(entity)
                _message.value = if (existing == null) "Expense saved on this device." else "Expense updated."
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not save this expense."
            }
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                _message.value = "Expense deleted."
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not delete this expense."
            }
        }
    }

    fun dateFor(expense: ExpenseEntity): String = LocalDate.ofEpochDay(expense.occurredOnEpochDay).toString()
}
