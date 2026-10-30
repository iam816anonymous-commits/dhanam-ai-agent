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
    @Query("SELECT * FROM recommendation_feedback ORDER BY created_at_epoch_millis DESC")
    fun observeRecommendationFeedback(): Flow<List<RecommendationFeedbackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecommendationFeedback(feedback: RecommendationFeedbackEntity): Long

    @Query("SELECT * FROM agent_skills ORDER BY status, created_at_epoch_millis DESC")
    fun observeAgentSkills(): Flow<List<AgentSkillEntity>>

    @Query("SELECT * FROM agent_skills WHERE skill_key = :skillKey ORDER BY version DESC LIMIT 1")
    suspend fun latestAgentSkill(skillKey: String): AgentSkillEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAgentSkillIfMissing(skill: AgentSkillEntity): Long

    @Query("SELECT * FROM agent_skills WHERE id = :id LIMIT 1")
    suspend fun findAgentSkill(id: Long): AgentSkillEntity?

    @Query("UPDATE agent_skills SET status = 'SUPERSEDED' WHERE skill_key = :skillKey AND status = 'ACTIVE' AND id != :exceptId")
    suspend fun supersedeActiveSkills(skillKey: String, exceptId: Long): Int

    @Query("UPDATE agent_skills SET status = 'ACTIVE', approved_at_epoch_millis = :approvedAt WHERE id = :id AND status = 'PROPOSED' AND test_total_count >= 3 AND test_pass_count * 100 >= test_total_count * 90")
    suspend fun activateProposedAgentSkill(id: Long, approvedAt: Long): Int

    @Transaction
    suspend fun approveAgentSkill(id: Long, approvedAt: Long): Int {
        val candidate = findAgentSkill(id) ?: return 0
        if (candidate.status != "PROPOSED" || candidate.testTotalCount < 3 ||
            candidate.testPassCount * 100 < candidate.testTotalCount * 90) return 0
        supersedeActiveSkills(candidate.skillKey, id)
        check(activateProposedAgentSkill(id, approvedAt) == 1) {
            "The skill proposal changed before it could be approved."
        }
        return 1
    }

    @Query("SELECT * FROM agent_skills WHERE skill_key = :skillKey AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    suspend fun findActiveAgentSkill(skillKey: String): AgentSkillEntity?

    @Query("SELECT * FROM agent_skills WHERE skill_key = :skillKey AND version < :version AND status = 'SUPERSEDED' ORDER BY version DESC LIMIT 1")
    suspend fun findPreviousAgentSkill(skillKey: String, version: Int): AgentSkillEntity?

    @Query("UPDATE agent_skills SET status = 'SUPERSEDED' WHERE id = :id AND status = 'ACTIVE'")
    suspend fun supersedeAgentSkill(id: Long): Int

    @Query("UPDATE agent_skills SET status = 'ACTIVE', approved_at_epoch_millis = :approvedAt WHERE id = :id AND status = 'SUPERSEDED'")
    suspend fun restoreAgentSkill(id: Long, approvedAt: Long): Int

    @Transaction
    suspend fun rollbackAgentSkill(skillKey: String, approvedAt: Long): Boolean {
        val active = findActiveAgentSkill(skillKey) ?: return false
        val previous = findPreviousAgentSkill(skillKey, active.version) ?: return false
        check(supersedeAgentSkill(active.id) == 1) { "The active skill changed before rollback." }
        check(restoreAgentSkill(previous.id, approvedAt) == 1) { "The previous skill could not be restored." }
        return true
    }

    @Query("UPDATE agent_skills SET status = 'REJECTED' WHERE id = :id AND status = 'PROPOSED'")
    suspend fun rejectAgentSkill(id: Long): Int

    @Query("SELECT * FROM agent_learning_reports ORDER BY report_date_epoch_day DESC LIMIT 30")
    fun observeAgentLearningReports(): Flow<List<AgentLearningReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentLearningReport(report: AgentLearningReportEntity): Long

    @Query("SELECT * FROM agent_memories ORDER BY agent_role, updated_at_epoch_millis DESC")
    fun observeAgentMemories(): Flow<List<AgentMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentMemory(memory: AgentMemoryEntity): Long

    @Query("SELECT * FROM agent_messages ORDER BY created_at_epoch_millis DESC LIMIT 100")
    fun observeAgentMessages(): Flow<List<AgentMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAgentMessage(message: AgentMessageEntity): Long

    @Query("DELETE FROM agent_messages WHERE created_at_epoch_millis < :cutoffEpochMillis")
    suspend fun pruneAgentMessages(cutoffEpochMillis: Long): Int

    @Query("DELETE FROM agent_memories")
    suspend fun clearAgentMemories(): Int

    @Query("DELETE FROM agent_messages")
    suspend fun clearAgentMessages(): Int

    @Query("SELECT * FROM income_records ORDER BY occurred_on_epoch_day DESC, id DESC")
    fun observeIncome(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM income_records WHERE id = :id LIMIT 1")
    suspend fun findIncome(id: Long): IncomeEntity?

    @Query("SELECT * FROM income_records WHERE occurred_on_epoch_day >= :startEpochDay ORDER BY occurred_on_epoch_day DESC, id DESC")
    suspend fun incomeSince(startEpochDay: Long): List<IncomeEntity>

    @Query("SELECT * FROM income_records ORDER BY occurred_on_epoch_day DESC, id DESC")
    suspend fun allIncome(): List<IncomeEntity>

    @Query("SELECT * FROM income_records WHERE source_record_key = :sourceId LIMIT 1")
    suspend fun findIncomeBySourceRecordKey(sourceId: String): IncomeEntity?

    @Query("SELECT * FROM income_records WHERE title = :title AND amount_paise = :amountPaise AND category = :category AND occurred_on_epoch_day = :dateEpochDay AND note = :note LIMIT 1")
    suspend fun findExactIncome(title: String, amountPaise: Long, category: String, dateEpochDay: Long, note: String): IncomeEntity?

    @Transaction
    suspend fun importIncomeSkippingExactDuplicates(records: List<IncomeEntity>): Int {
        var insertedCount = 0
        for (record in records) {
            val alreadyImported = record.sourceRecordKey?.let { findIncomeBySourceRecordKey(it) != null }
                ?: (findExactIncome(record.title, record.amountPaise, record.category,
                    record.occurredOnEpochDay, record.note) != null)
            if (!alreadyImported) {
                insertIncome(record)
                insertedCount++
            }
        }
        return insertedCount
    }

    @Insert
    suspend fun insertIncome(income: IncomeEntity): Long

    @Update
    suspend fun updateIncome(income: IncomeEntity): Int

    @Query("DELETE FROM income_records WHERE id = :id")
    suspend fun deleteIncomeById(id: Long): Int

    @Query("SELECT * FROM expenses ORDER BY occurred_on_epoch_day DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE occurred_on_epoch_day >= :startEpochDay ORDER BY occurred_on_epoch_day DESC, id DESC")
    suspend fun expensesSince(startEpochDay: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findExpense(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE source_record_key = :sourceId LIMIT 1")
    suspend fun findExpenseBySourceRecordKey(sourceId: String): ExpenseEntity?

    @Insert suspend fun insertExpense(expense: ExpenseEntity): Long
    @Update suspend fun updateExpense(expense: ExpenseEntity): Int
    @Delete suspend fun deleteExpense(expense: ExpenseEntity): Int

    @Query("SELECT * FROM budgets WHERE month_epoch_day = :monthEpochDay ORDER BY category")
    fun observeBudgetsForMonth(monthEpochDay: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category AND month_epoch_day = :monthEpochDay LIMIT 1")
    suspend fun findBudget(category: String, monthEpochDay: Long): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY month_epoch_day, category")
    suspend fun allBudgets(): List<BudgetEntity>

    @Insert suspend fun insertBudget(budget: BudgetEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBudgetIfMissing(budget: BudgetEntity): Long
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

    @Query("SELECT * FROM khata_loans WHERE legacy_source_id = :sourceId LIMIT 1")
    suspend fun findLoanByLegacySourceId(sourceId: String): KhataLoanEntity?
    @Update suspend fun updateLoan(loan: KhataLoanEntity): Int

    @Transaction
    suspend fun updateLoanIfUnchanged(loan: KhataLoanEntity, expectedPaidPaise: Long): Boolean {
        val current = findLoan(loan.id) ?: return false
        require(current.paidPaise == expectedPaidPaise) {
            "A repayment was recorded while this Khata record was being edited. Refresh and review the new balance."
        }
        require(current.direction == loan.direction) { "The lending/borrowing direction cannot be changed." }
        require(loan.principalPaise >= current.paidPaise) { "Principal cannot be lower than repayments already recorded." }
        return updateLoan(
            loan.copy(
                paidPaise = current.paidPaise,
                isSettled = loan.principalPaise == current.paidPaise
            )
        ) == 1
    }

    @Query("""SELECT * FROM khata_loans
        WHERE person_name = :personName AND direction = :direction
        AND principal_paise = :principalPaise AND paid_paise = :paidPaise
        AND opened_on_epoch_day = :openedOnEpochDay
        AND ((due_on_epoch_day IS NULL AND :dueOnEpochDay IS NULL) OR due_on_epoch_day = :dueOnEpochDay)
        AND note = :note AND phone_number = :phoneNumber AND is_settled = :isSettled
        LIMIT 1""")
    suspend fun findMatchingLoan(personName: String, direction: String, principalPaise: Long, paidPaise: Long,
        openedOnEpochDay: Long, dueOnEpochDay: Long?, note: String, phoneNumber: String, isSettled: Boolean): KhataLoanEntity?

    @Query("SELECT * FROM khata_repayments WHERE loan_id = :loanId ORDER BY paid_on_epoch_day DESC, id DESC")
    fun observeRepayments(loanId: Long): Flow<List<RepaymentEntity>>

    @Query("SELECT * FROM khata_repayments WHERE request_id = :requestId LIMIT 1")
    suspend fun findRepaymentByRequestId(requestId: String): RepaymentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepayment(repayment: RepaymentEntity): Long

    @Query("""UPDATE khata_loans
        SET paid_paise = paid_paise + :amountPaise,
            is_settled = CASE WHEN principal_paise - paid_paise = :amountPaise THEN 1 ELSE is_settled END,
            updated_at_epoch_millis = :now
        WHERE id = :loanId AND is_settled = 0 AND principal_paise - paid_paise >= :amountPaise""")
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

    @Transaction
    suspend fun importExpensesSkippingExactDuplicates(expenses: List<ExpenseEntity>): Int {
        var insertedCount = 0
        for (expense in expenses) {
            val alreadyImported = expense.sourceRecordKey?.let { findExpenseBySourceRecordKey(it) != null }
                ?: (findExactExpense(expense.title, expense.amountPaise, expense.category,
                    expense.occurredOnEpochDay, expense.note) != null)
            if (!alreadyImported) {
                insertExpense(expense)
                insertedCount++
            }
        }
        return insertedCount
    }

    @Transaction
    suspend fun importLegacyRecords(
        expenses: List<ExpenseEntity>,
        loans: List<KhataLoanEntity>,
        budgets: List<BudgetEntity>
    ): LegacyImportCounts {
        var expenseCount = 0
        var loanCount = 0
        var budgetCount = 0
        for (expense in expenses) {
            val alreadyImported = expense.sourceRecordKey?.let { findExpenseBySourceRecordKey(it) != null }
                ?: (findExactExpense(expense.title, expense.amountPaise, expense.category,
                    expense.occurredOnEpochDay, expense.note) != null)
            if (!alreadyImported) {
                insertExpense(expense)
                expenseCount++
            }
        }
        for (loan in loans) {
            val alreadyImported = loan.legacySourceId?.let { findLoanByLegacySourceId(it) != null }
                ?: (findMatchingLoan(loan.personName, loan.direction, loan.principalPaise, loan.paidPaise,
                    loan.openedOnEpochDay, loan.dueOnEpochDay, loan.note, loan.phoneNumber, loan.isSettled) != null)
            if (!alreadyImported) {
                insertLoan(loan)
                loanCount++
            }
        }
        for (budget in budgets) {
            if (findBudget(budget.category, budget.monthEpochDay) == null &&
                insertBudgetIfMissing(budget) != -1L) budgetCount++
        }
        return LegacyImportCounts(expenseCount, loanCount, budgetCount)
    }

    @Query("SELECT * FROM expenses WHERE title = :title AND amount_paise = :amountPaise AND category = :category AND occurred_on_epoch_day = :dateEpochDay AND note = :note LIMIT 1")
    suspend fun findExactExpense(title: String, amountPaise: Long, category: String, dateEpochDay: Long, note: String): ExpenseEntity?
}
