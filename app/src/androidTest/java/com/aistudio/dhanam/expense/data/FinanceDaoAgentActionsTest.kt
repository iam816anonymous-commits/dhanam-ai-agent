package com.aistudio.dhanam.expense.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aistudio.dhanam.expense.domain.KhataValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDaoAgentActionsTest {
    private lateinit var database: FinanceDatabase
    private lateinit var dao: FinanceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.financeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun expenseReadBackAndBudgetUpsertRemainConsistent() = runBlocking {
        val expenseId = dao.insertExpense(
            ExpenseEntity(
                title = "lunch",
                amountPaise = 25_000L,
                category = "Food",
                occurredOnEpochDay = 20_000L,
                note = "",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        val saved = dao.findExpense(expenseId)
        assertEquals("lunch", saved?.title)
        assertEquals(25_000L, saved?.amountPaise)

        val month = 20_000L
        dao.saveBudget("Food", month, 300_000L, 1L)
        dao.saveBudget("Food", month, 350_000L, 2L)
        assertEquals(350_000L, dao.findBudget("Food", month)?.amountPaise)
        assertEquals(1, dao.allBudgets().count { it.category == "Food" && it.monthEpochDay == month })
    }

    @Test
    fun repaymentIsIdempotentAndCannotOverpay() = runBlocking {
        val loanId = dao.insertLoan(
            KhataLoanEntity(
                personName = "Ravi",
                direction = KhataValidator.LENT,
                principalPaise = 10_000L,
                paidPaise = 0L,
                openedOnEpochDay = 20_000L,
                dueOnEpochDay = null,
                note = "",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
        )
        val repayment = RepaymentEntity(
            loanId = loanId,
            amountPaise = 4_000L,
            paidOnEpochDay = 20_001L,
            note = "test",
            requestId = "agent-request-once",
            createdAtEpochMillis = 2L
        )
        assertTrue(dao.recordRepayment(repayment))
        assertFalse(dao.recordRepayment(repayment))
        assertEquals(4_000L, dao.findLoan(loanId)?.paidPaise)
        assertEquals(6_000L, dao.findLoan(loanId)?.outstandingPaise)

        var rejected = false
        try {
            dao.recordRepayment(repayment.copy(amountPaise = 7_000L, requestId = "agent-overpayment"))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Overpayment must be rejected", rejected)
        assertEquals(1, dao.allRepayments().size)
        assertEquals(4_000L, dao.findLoan(loanId)?.paidPaise)
    }
}
