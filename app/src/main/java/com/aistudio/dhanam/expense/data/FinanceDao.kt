package com.aistudio.dhanam.expense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM expenses ORDER BY occurred_on_epoch_day DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findExpense(id: Long): ExpenseEntity?

    @Insert suspend fun insertExpense(expense: ExpenseEntity): Long
    @Update suspend fun updateExpense(expense: ExpenseEntity): Int
    @Delete suspend fun deleteExpense(expense: ExpenseEntity): Int

    @Query("SELECT * FROM budgets WHERE month_epoch_day = :monthEpochDay ORDER BY category")
    fun observeBudgetsForMonth(monthEpochDay: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category AND month_epoch_day = :monthEpochDay LIMIT 1")
    suspend fun findBudget(category: String, monthEpochDay: Long): BudgetEntity?

    @Insert suspend fun insertBudget(budget: BudgetEntity): Long
    @Update suspend fun updateBudget(budget: BudgetEntity): Int

    @Transaction
    suspend fun saveBudget(category: String, monthEpochDay: Long, amountPaise: Long, now: Long) {
        require(category.isNotBlank()) { "Choose a budget category." }
        require(amountPaise > 0) { "Budget must be greater than zero." }
        val existing = findBudget(category, monthEpochDay)
        if (existing == null) {
            insertBudget(BudgetEntity(category = category, monthEpochDay = monthEpochDay,
                amountPaise = amountPaise, createdAtEpochMillis = now, updatedAtEpochMillis = now))
        } else {
            check(updateBudget(existing.copy(amountPaise = amountPaise, updatedAtEpochMillis = now)) == 1) {
                "Budget changed before it could be saved. Refresh and try again."
            }
        }
    }

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Long): Int

    @Query("SELECT * FROM khata_loans ORDER BY opened_on_epoch_day DESC, id DESC")
    fun observeLoans(): Flow<List<KhataLoanEntity>>

    @Query("SELECT * FROM khata_loans WHERE id = :id LIMIT 1")
    suspend fun findLoan(id: Long): KhataLoanEntity?

    @Insert suspend fun insertLoan(loan: KhataLoanEntity): Long
    @Update suspend fun updateLoan(loan: KhataLoanEntity): Int

    @Query("SELECT * FROM khata_repayments WHERE loan_id = :loanId ORDER BY paid_on_epoch_day DESC, id DESC")
    fun observeRepayments(loanId: Long): Flow<List<RepaymentEntity>>

    @Query("SELECT * FROM khata_repayments WHERE request_id = :requestId LIMIT 1")
    suspend fun findRepaymentByRequestId(requestId: String): RepaymentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepayment(repayment: RepaymentEntity): Long

    @Query("UPDATE khata_loans SET paid_paise = paid_paise + :amountPaise, updated_at_epoch_millis = :now WHERE id = :loanId AND principal_paise - paid_paise >= :amountPaise")
    suspend fun incrementPaid(loanId: Long, amountPaise: Long, now: Long): Int

    @Transaction
    suspend fun recordRepayment(repayment: RepaymentEntity): Boolean {
        require(repayment.amountPaise > 0) { "Repayment must be greater than zero." }
        require(repayment.requestId.isNotBlank()) { "A repayment request ID is required." }
        if (findRepaymentByRequestId(repayment.requestId) != null) return false
        val loan = findLoan(repayment.loanId) ?: error("This Khata record no longer exists.")
        require(repayment.amountPaise <= loan.outstandingPaise) { "Repayment exceeds the outstanding amount." }
        val inserted = insertRepayment(repayment)
        if (inserted == -1L) return false
        check(incrementPaid(loan.id, repayment.amountPaise, repayment.createdAtEpochMillis) == 1) {
            "Outstanding amount changed. Refresh and try again."
        }
        return true
    }

    @Query("DELETE FROM khata_loans WHERE id = :id AND NOT EXISTS (SELECT 1 FROM khata_repayments WHERE loan_id = :id)")
    suspend fun deleteLoanIfNoRepayments(id: Long): Int

    @Query("SELECT * FROM khata_repayments ORDER BY paid_on_epoch_day DESC, id DESC")
    suspend fun allRepayments(): List<RepaymentEntity>

    @Insert suspend fun insertExpenses(expenses: List<ExpenseEntity>): List<Long>

    @Transaction
    suspend fun importExpensesSkippingExactDuplicates(expenses: List<ExpenseEntity>): Int {
        var insertedCount = 0
        for (expense in expenses) {
            val existing = findExactExpense(
                expense.title, expense.amountPaise, expense.category,
                expense.occurredOnEpochDay, expense.note
            )
            if (existing == null) {
                insertExpense(expense)
                insertedCount++
            }
        }
        return insertedCount
    }

    @Query("SELECT * FROM expenses WHERE title = :title AND amount_paise = :amountPaise AND category = :category AND occurred_on_epoch_day = :dateEpochDay AND note = :note LIMIT 1")
    suspend fun findExactExpense(title: String, amountPaise: Long, category: String, dateEpochDay: Long, note: String): ExpenseEntity?
}
