package com.aistudio.dhanam.expense.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.dhanam.expense.data.FinanceDatabase
import com.aistudio.dhanam.expense.domain.FinanceCalculations
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class FinanceReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(FinanceReminderScheduler.PREFS, Context.MODE_PRIVATE)
        val budgetEnabled = prefs.getBoolean(FinanceReminderScheduler.BUDGET_KEY, false)
        val khataEnabled = prefs.getBoolean(FinanceReminderScheduler.KHATA_KEY, false)
        if (!budgetEnabled && !khataEnabled) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        return try {
            val db = FinanceDatabase.getInstance(applicationContext)
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            val messages = mutableListOf<String>()
            if (budgetEnabled) {
                val expenses = db.expenseDao().observeAll().first()
                val budgets = db.financeDao().observeBudgetsForMonth(month.atDay(1).toEpochDay()).first()
                val summary = FinanceCalculations.monthlySummary(expenses, budgets, month, today)
                val budget = summary.overallBudgetPaise
                if (budget != null && budget > 0 && summary.totalSpentPaise >= budget - budget / 5) {
                    messages += "Your monthly budget is at least 80% used. Open Dhanam to review."
                }
            }
            if (khataEnabled) {
                val dueSoon = db.financeDao().observeLoans().first().any { loan ->
                    loan.outstandingPaise > 0 && loan.dueOnEpochDay != null &&
                        loan.dueOnEpochDay in today.toEpochDay()..today.plusDays(3).toEpochDay()
                }
                if (dueSoon) messages += "A Khata record is due within the next three days. Open Dhanam to review."
            }
            if (messages.isNotEmpty()) notify(messages.joinToString(" "))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Finance reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Private budget and Khata reminders" })
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dhanam reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Permission can be revoked after the pre-check. */ }
    }

    companion object {
        private const val CHANNEL_ID = "dhanam_finance_reminders"
        private const val NOTIFICATION_ID = 2001
    }
}
