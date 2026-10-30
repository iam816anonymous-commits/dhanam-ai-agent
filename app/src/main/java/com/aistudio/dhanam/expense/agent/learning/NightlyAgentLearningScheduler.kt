package com.aistudio.dhanam.expense.agent.learning

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NightlyAgentLearningScheduler {
    const val ENABLED_PREFERENCE = "nightly_agent_learning_enabled"
    const val WORK_TAG = "dhanam-nightly-agent-learning"

    fun scheduleNext(context: Context, forceTomorrow: Boolean = false) {
        val preferences = context.getSharedPreferences("agent_learning", Context.MODE_PRIVATE)
        if (!preferences.getBoolean(ENABLED_PREFERENCE, false)) return

        val now = ZonedDateTime.now()
        val todayOneAm = now.toLocalDate().atTime(NIGHT_START).atZone(now.zone)
        val target = when {
            forceTomorrow -> todayOneAm.plusDays(1)
            now.isBefore(todayOneAm) -> todayOneAm
            isWithinWindow(now.toLocalTime()) -> now
            else -> todayOneAm.plusDays(1)
        }
        val delayMillis = Duration.between(now, target).toMillis().coerceAtLeast(0L)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        val request = OneTimeWorkRequestBuilder<NightlyAgentLearningWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        val uniqueName = "dhanam-nightly-agent-learning-${target.toLocalDate()}"
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    fun isWithinWindow(time: LocalTime): Boolean =
        !time.isBefore(NIGHT_START) && time.isBefore(NIGHT_END)

    private val NIGHT_START = LocalTime.of(1, 0)
    private val NIGHT_END = LocalTime.of(6, 0)
}
