package com.aistudio.dhanam.expense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.IncomeEntity
import com.aistudio.dhanam.expense.domain.IncomeInputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class IncomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FinanceDatabase.getInstance(application).financeDao()
    val income: StateFlow<List<IncomeEntity>> =
        dao.observeIncome().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun saveIncome(id: Long?, title: String, amount: String, category: String, date: String, note: String) {
        viewModelScope.launch {
            try {
                val input = IncomeInputValidator.validate(title, amount, category, date, note)
                val now = System.currentTimeMillis()
                val existing = id?.let { dao.findIncome(it) }
                if (id != null && existing == null) error("This income record no longer exists. Refresh and try again.")
                val record = IncomeEntity(
                    id = existing?.id ?: 0L,
                    title = input.title,
                    amountPaise = input.amountPaise,
                    category = input.category,
                    occurredOnEpochDay = input.occurredOn.toEpochDay(),
                    note = input.note,
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now
                )
                if (existing == null) dao.insertIncome(record)
                else check(dao.updateIncome(record) == 1) { "Income record changed before it could be updated. Refresh and try again." }
                _message.value = if (existing == null) "Income saved on this device." else "Income updated."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = error.message ?: "Could not save income."
            }
        }
    }

    fun deleteIncome(id: Long) {
        viewModelScope.launch {
            try {
                check(dao.deleteIncomeById(id) == 1) { "This income record no longer exists." }
                _message.value = "Income record deleted."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = error.message ?: "Could not delete income."
            }
        }
    }

    fun dateFor(record: IncomeEntity): String = LocalDate.ofEpochDay(record.occurredOnEpochDay).toString()
}
