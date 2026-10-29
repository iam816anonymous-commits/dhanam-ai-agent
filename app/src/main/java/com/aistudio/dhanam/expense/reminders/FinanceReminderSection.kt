package com.aistudio.dhanam.expense.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun FinanceReminderSection() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(FinanceReminderScheduler.PREFS, 0) }
    var budgetEnabled by remember { mutableStateOf(prefs.getBoolean(FinanceReminderScheduler.BUDGET_KEY, false)) }
    var khataEnabled by remember { mutableStateOf(prefs.getBoolean(FinanceReminderScheduler.KHATA_KEY, false)) }
    var pendingChange by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun persist(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        budgetEnabled = prefs.getBoolean(FinanceReminderScheduler.BUDGET_KEY, false)
        khataEnabled = prefs.getBoolean(FinanceReminderScheduler.KHATA_KEY, false)
        FinanceReminderScheduler.sync(context)
        message = if (enabled) "Reminder preference saved." else "Reminder preference disabled."
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pendingChange
        pendingChange = null
        if (granted && key != null) persist(key, true)
        else message = "Notification permission was not granted; reminder remains disabled."
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            Text("WorkManager checks periodically. Android battery controls can delay the check; these are not exact-time alarms.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReminderToggle("Budget reaches 80% of monthly limit", budgetEnabled) { enabled ->
                if (!enabled) persist(FinanceReminderScheduler.BUDGET_KEY, false)
                else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    pendingChange = FinanceReminderScheduler.BUDGET_KEY
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else persist(FinanceReminderScheduler.BUDGET_KEY, true)
            }
            ReminderToggle("Khata due in the next 3 days", khataEnabled) { enabled ->
                if (!enabled) persist(FinanceReminderScheduler.KHATA_KEY, false)
                else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    pendingChange = FinanceReminderScheduler.KHATA_KEY
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else persist(FinanceReminderScheduler.KHATA_KEY, true)
            }
            if (message != null) Text(message.orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
