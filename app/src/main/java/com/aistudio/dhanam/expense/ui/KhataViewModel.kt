package com.aistudio.dhanam.expense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.data.KhataLoanEntity
import com.aistudio.dhanam.expense.data.RepaymentEntity
import com.aistudio.dhanam.expense.domain.KhataValidator
import com.aistudio.dhanam.expense.domain.MoneyParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class KhataViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FinanceDatabase.getInstance(application).financeDao()
    val loans: StateFlow<List<KhataLoanEntity>> =
        dao.observeLoans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }
    fun repaymentsFor(loanId: Long): Flow<List<RepaymentEntity>> = dao.observeRepayments(loanId)

    fun addLoan(person: String, direction: String, amount: String, opened: String, due: String, note: String, phoneNumber: String = "") {
        viewModelScope.launch {
            try {
                val input = KhataValidator.validate(person, direction, amount, opened, due, note, phoneNumber)
                val now = System.currentTimeMillis()
                dao.insertLoan(KhataLoanEntity(
                    personName = input.personName, direction = input.direction,
                    principalPaise = input.principalPaise, paidPaise = 0,
                    openedOnEpochDay = input.openedOn.toEpochDay(), dueOnEpochDay = input.dueOn?.toEpochDay(),
                    note = input.note, createdAtEpochMillis = now, updatedAtEpochMillis = now,
                    phoneNumber = input.phoneNumber
                ))
                _message.value = "Khata record saved."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Could not save the Khata record." }
        }
    }

    fun updateLoan(id: Long, person: String, direction: String, amount: String, opened: String, due: String, note: String, phoneNumber: String = "") {
        viewModelScope.launch {
            try {
                val existing = dao.findLoan(id) ?: error("This Khata record no longer exists. Refresh and try again.")
                require(direction == existing.direction) { "The lending/borrowing direction cannot be changed when editing a saved record." }
                val input = KhataValidator.validate(person, direction, amount, opened, due, note, phoneNumber)
                require(input.principalPaise >= existing.paidPaise) {
                    "Principal cannot be lower than repayments already recorded."
                }
                val updated = existing.copy(
                    personName = input.personName,
                    principalPaise = input.principalPaise,
                    openedOnEpochDay = input.openedOn.toEpochDay(),
                    dueOnEpochDay = input.dueOn?.toEpochDay(),
                    note = input.note,
                    phoneNumber = input.phoneNumber,
                    isSettled = input.principalPaise == existing.paidPaise,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
                check(dao.updateLoan(updated) == 1) { "Khata record changed before it could be updated. Refresh and try again." }
                _message.value = "Khata record updated."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Could not update the Khata record." }
        }
    }

    fun recordRepayment(loanId: Long, amount: String, date: String, note: String, requestId: String = UUID.randomUUID().toString()) {
        viewModelScope.launch {
            try {
                val paise = MoneyParser.parseRupeesToPaise(amount)
                val paidDate = LocalDate.parse(date.trim())
                val now = System.currentTimeMillis()
                val inserted = dao.recordRepayment(RepaymentEntity(
                    loanId = loanId, amountPaise = paise, paidOnEpochDay = paidDate.toEpochDay(),
                    note = note.trim().take(1000), requestId = requestId, createdAtEpochMillis = now
                ))
                _message.value = if (inserted) "Repayment recorded." else "This repayment request was already recorded."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Could not record repayment." }
        }
    }

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            try {
                check(dao.deleteLoanIfNoRepayments(id) == 1) {
                    "A record with repayment history cannot be deleted. Keep its audit trail."
                }
                _message.value = "Khata record deleted."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.message ?: "Could not delete the record." }
        }
    }
}
