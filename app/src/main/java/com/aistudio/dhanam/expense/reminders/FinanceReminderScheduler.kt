package com.aistudio.dhanam.expense.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object FinanceReminderScheduler {
    const val PREFS = "dhanam_reminders"
    const val BUDGET_KEY = "budget_reminders_enabled"
    const val KHATA_KEY = "khata_due_reminders_enabled"
    const val WORK_NAME = "dhanam-finance-reminders"

    fun sync(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(BUDGET_KEY, false) || prefs.getBoolean(KHATA_KEY, false)
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FinanceReminderWorker>(24, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
