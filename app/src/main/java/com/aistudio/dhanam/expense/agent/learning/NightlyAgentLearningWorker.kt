package com.aistudio.dhanam.expense.agent.learning

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.dhanam.expense.agent.FinancialAnalysisEngine
import com.aistudio.dhanam.expense.data.AgentLearningReportEntity
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * First safe nightly slice: prepare a local evidence report without claiming that model-based
 * learning occurred. AI tasks are added only after the shared runtime can be acquired safely
 * from a background worker without creating a second Engine.
 */
class NightlyAgentLearningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("agent_learning", Context.MODE_PRIVATE)
        if (!preferences.getBoolean(NightlyAgentLearningScheduler.ENABLED_PREFERENCE, false)) {
            return Result.success()
        }
        val now = java.time.LocalTime.now()
        if (!NightlyAgentLearningScheduler.isWithinWindow(now)) {
            NightlyAgentLearningScheduler.scheduleNext(applicationContext)
            return Result.success()
        }

        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val today = LocalDate.now()
            val monthStart = YearMonth.from(today).atDay(1).toEpochDay()
            val db = FinanceDatabase.getInstance(applicationContext)
            val expenses = db.expenseDao().observeAll().first()
            val budgets = db.financeDao().observeBudgetsForMonth(monthStart).first()
            val loans = db.financeDao().observeLoans().first()
            val snapshot = FinancialAnalysisEngine.analyze(expenses, budgets, loans, today)

            val intelligenceSummary = if (snapshot.opportunities.isEmpty()) {
                "No high-confidence savings review was identified from the available ledger data."
            } else {
                snapshot.opportunities.take(8).joinToString("\n") { opportunity ->
                    "${opportunity.title}: ${opportunity.evidence} Next step: ${opportunity.suggestedAction}"
                }.take(4_000)
            }
            val operationsSummary = buildString {
                append("Checked ${expenses.size} recorded expense(s), ${loans.size} Khata record(s), and ${budgets.size} budget(s). ")
                append("Outstanding lent: ${MoneyParser.formatPaise(snapshot.lentOutstandingPaise)}. ")
                append("Outstanding borrowed: ${MoneyParser.formatPaise(snapshot.borrowedOutstandingPaise)}.")
            }
            val skillSummary =
                "Preparation-only run. No AI skill was learned, tested, activated, or changed. The skill agent is waiting for the background-safe shared model runtime and approval pipeline."
            val nextImprovements = if (snapshot.opportunities.isEmpty()) {
                "Collect a longer transaction history; do not infer savings from missing data."
            } else {
                "Review the evidence-backed opportunities above, verify each underlying transaction, and test only reversible discretionary changes."
            }
            db.financeDao().upsertAgentLearningReport(
                AgentLearningReportEntity(
                    reportDateEpochDay = today.toEpochDay(),
                    runStatus = "PREPARATION_ONLY",
                    operationsSummary = operationsSummary,
                    intelligenceSummary = intelligenceSummary,
                    skillSummary = skillSummary,
                    nextImprovements = nextImprovements,
                    durationMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
            NightlyAgentLearningScheduler.scheduleNext(applicationContext)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
