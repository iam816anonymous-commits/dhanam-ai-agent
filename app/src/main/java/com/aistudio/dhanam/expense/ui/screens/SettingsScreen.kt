package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.security.PinSettingsSection
import com.aistudio.dhanam.expense.agent.learning.AgentLearningSection
import com.aistudio.dhanam.expense.reminders.FinanceReminderSection
import com.aistudio.dhanam.expense.ui.components.FinanceDataSection
import com.aistudio.dhanam.expense.ui.components.LegacyMigrationSection
import com.aistudio.dhanam.expense.ui.components.ModelManagerSection
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel

@Composable
fun SettingsScreen(
    inferenceViewModel: LocalInferenceViewModel,
    isDarkTheme: Boolean = true,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var screenshotPrivacy by remember(context) {
        mutableStateOf(context.getSharedPreferences("dhanam_privacy", 0).getBoolean("screenshot_privacy_enabled", false))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Privacy, backups, reports and on-device AI", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsInfoCard(
            title = "Financial privacy",
            detail = "Financial records are stored in the local Room database. Android backup is disabled for this app; CSV and PDF files are written only to destinations you explicitly choose. The app does not upload transaction data."
        )
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dark appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Keep the Dhanam emerald-and-gold palette in dark mode, or switch to the light palette.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Screenshot privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Block screenshots, screen recording and recent-app previews on financial screens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = screenshotPrivacy,
                    onCheckedChange = { enabled ->
                        screenshotPrivacy = enabled
                        context.getSharedPreferences("dhanam_privacy", 0).edit()
                            .putBoolean("screenshot_privacy_enabled", enabled).apply()
                        if (enabled) {
                            activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
        FinanceDataSection()
        LegacyMigrationSection()
        ModelManagerSection(inferenceViewModel = inferenceViewModel)
        AgentLearningSection()
        PinSettingsSection()
        FinanceReminderSection()
        SettingsInfoCard(
            title = "Security notes",
            detail = "The optional PIN gate locks the app when it leaves the foreground and stores a salted PBKDF2 hash. It does not encrypt the database against device-level forensic access; use your device screen lock as well."
        )
        SettingsInfoCard(title = "Version", detail = "Dhanam AI 0.7.0 · Local model inference preview")
    }
}

@Composable
private fun SettingsInfoCard(title: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
