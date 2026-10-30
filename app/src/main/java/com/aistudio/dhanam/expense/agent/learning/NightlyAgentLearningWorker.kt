package com.aistudio.dhanam.expense.agent.learning

import android.content.Context
import android.app.Application
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.dhanam.expense.agent.FinancialAnalysisEngine
import com.aistudio.dhanam.expense.agent.SkillLearningEngine
import com.aistudio.dhanam.expense.agent.AgentTaskPriority
import com.aistudio.dhanam.expense.agent.AgentTaskRequest
import com.aistudio.dhanam.expense.agent.DhanamAgentRole
import com.aistudio.dhanam.expense.model.LocalInferenceRuntimeRegistry
import kotlinx.coroutines.CancellationException
import com.aistudio.dhanam.expense.data.AgentLearningReportEntity
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.time.LocalDate
import java.time.YearMonth
import java.time.Duration
import kotlinx.coroutines.flow.first

/**
 * Nightly finance workflow. It uses deterministic local ledger analysis first, then optionally
 * loads the user's already-downloaded preferred model through the same process-wide runtime
 * used by the UI. All three specialist calls are serialized and remain inside the 01:00–06:00 window.
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
            val skillCandidates = SkillLearningEngine.propose(expenses)
            var newSkillProposals = 0
            skillCandidates.forEach { candidate ->
                val latest = db.financeDao().latestAgentSkill(candidate.skillKey)
                val shouldPropose = latest == null ||
                    (latest.category != candidate.category && latest.status != "PROPOSED")
                if (shouldPropose) {
                    val nextVersion = (latest?.version ?: 0) + 1
                    val inserted = db.financeDao().insertAgentSkillIfMissing(
                        com.aistudio.dhanam.expense.data.AgentSkillEntity(
                            skillKey = candidate.skillKey,
                            version = nextVersion,
                            title = candidate.title,
                            matchText = candidate.matchText,
                            category = candidate.category,
                            exampleCount = candidate.exampleCount,
                            testPassCount = candidate.testPassCount,
                            testTotalCount = candidate.testTotalCount,
                            status = "PROPOSED",
                            createdAtEpochMillis = System.currentTimeMillis()
                        )
                    )
                    if (inserted != -1L) newSkillProposals++
                }
            }

            val deterministicIntelligence = if (snapshot.opportunities.isEmpty()) {
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
            val modelOwner = LocalInferenceRuntimeRegistry.getOrCreate(applicationContext.applicationContext as Application)
            val modelWasReady = modelOwner.state.value.isReady
            val minutesLeft = Duration.between(java.time.LocalTime.now(), java.time.LocalTime.of(6, 0)).toMinutes()
            val modelReady = modelWasReady || (minutesLeft >= 10 && modelOwner.loadModelAndAwait())
            var modelInsightSummary = "The verified preferred model was not available or there was insufficient time remaining in the 01:00–06:00 window. Deterministic finance analysis and skill tests still ran."
            var modelSkillSummary = "No model-based skill review was run; no untested skill was activated."
            var modelInsightCompleted = false
            var modelAgentTasksCompleted = 0
            var operationsSummaryWithModel = operationsSummary
            if (modelReady && NightlyAgentLearningScheduler.isWithinWindow(java.time.LocalTime.now())) {
                try {
                    val operationsPrompt = "Review this local financial ledger integrity summary. Identify only finance-record checks or inconsistencies worth reviewing. Do not invent data or propose writes.\n$operationsSummary"
                    val operationsReview = modelOwner.sharedAgentCoordinator.run(
                        AgentTaskRequest(DhanamAgentRole.FINANCE_OPERATIONS, operationsPrompt,
                            AgentTaskPriority.BACKGROUND, requiresNightWindow = true)
                    ).output.take(900)
                    operationsSummaryWithModel += "\n\nOn-device operations review (advisory): $operationsReview"
                    modelAgentTasksCompleted++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    operationsSummaryWithModel += "\n\nModel operations review not completed: ${error.message ?: "runtime unavailable"}."
                }
                try {
                    val categoryEvidence = snapshot.categoryMovements.take(7).joinToString("\n") {
                        "${it.category}: current ${it.currentMonthPaise} paise; comparable previous ${it.previousMonthPaise} paise; change ${it.changePaise} paise."
                    }
                    val financePrompt = buildString {
                        append("Explain these deterministic local-ledger findings concisely. Do not calculate new totals, invent facts, recommend cutting essential needs, or claim an action was taken. ")
                        append("Treat supplied values as authoritative evidence and distinguish observations from suggestions.\n")
                        append("Current month total: ${snapshot.totalSpentPaise} paise. Comparable previous month-to-date total: ${snapshot.monthToDateComparablePaise} paise.\n")
                        append(categoryEvidence)
                        append("\nEvidence-backed opportunities:\n")
                        append(deterministicIntelligence)
                    }
                    modelInsightSummary = modelOwner.sharedAgentCoordinator.run(
                        AgentTaskRequest(DhanamAgentRole.FINANCIAL_INTELLIGENCE, financePrompt,
                            AgentTaskPriority.BACKGROUND, requiresNightWindow = true)
                    ).output.take(1_200)
                    modelInsightCompleted = true
                    modelAgentTasksCompleted++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    modelInsightSummary = "Model financial explanation not completed: ${error.message ?: "runtime unavailable"}."
                }
                try {
                    val skillEvidence = skillCandidates
                        .groupingBy { it.category }
                        .fold(0) { count, candidate -> count + candidate.exampleCount }
                        .entries
                        .joinToString("\n") { "${it.key}: ${it.value} local examples across tested candidate rules." }
                    val skillPrompt = "Review these aggregate results from already-tested finance-only workflow rules. Suggest safer test cases or validation improvements. Do not ask for raw transaction descriptions, approve, activate, or rewrite rules.\n$skillEvidence"
                    modelSkillSummary = modelOwner.sharedAgentCoordinator.run(
                        AgentTaskRequest(DhanamAgentRole.SKILL_IMPROVEMENT, skillPrompt,
                            AgentTaskPriority.BACKGROUND, requiresNightWindow = true)
                    ).output.take(1_200)
                    modelAgentTasksCompleted++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    modelSkillSummary = "Model skill review not completed: ${error.message ?: "runtime unavailable"}."
                }
            }
            val intelligenceSummary = deterministicIntelligence +
                if (modelInsightCompleted) "\n\nOn-device model explanation (advisory; verify against the evidence above):\n$modelInsightSummary"
                else "\n\n$modelInsightSummary"

            val skillSummary = if (newSkillProposals == 0) {
                "The skill agent tested repeated expense descriptions but found no new category rule that passed leave-one-out validation. No skill was activated. $modelSkillSummary"
            } else {
                "Proposed $newSkillProposals tested exact-description category skill(s). Candidates passed leave-one-out checks against local history. None was activated automatically; user approval is required. $modelSkillSummary"
            }
            val nextImprovements = buildString {
                if (snapshot.opportunities.isEmpty()) {
                    append("Collect a longer transaction history; do not infer savings from missing data. ")
                } else {
                    append("Review evidence-backed savings opportunities and test only reversible discretionary changes. ")
                }
                if (newSkillProposals > 0) append("Review and approve or reject the proposed category skills in Settings.")
                else append("Continue collecting consistent transaction descriptions so the skill agent can evaluate them.")
            }
            db.financeDao().upsertAgentLearningReport(
                AgentLearningReportEntity(
                    reportDateEpochDay = today.toEpochDay(),
                    runStatus = if (modelAgentTasksCompleted > 0) "MODEL_AND_RULE_LEARNING" else "RULE_LEARNING_ONLY",
                    operationsSummary = operationsSummaryWithModel.take(2_000),
                    intelligenceSummary = intelligenceSummary.take(4_000),
                    skillSummary = skillSummary.take(2_000),
                    nextImprovements = nextImprovements.take(2_000),
                    durationMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
            if (!modelWasReady && modelReady) modelOwner.unloadModelIfIdle()
            NightlyAgentLearningScheduler.scheduleNext(applicationContext, forceTomorrow = true)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
