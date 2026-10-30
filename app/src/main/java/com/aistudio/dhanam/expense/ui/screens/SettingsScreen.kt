package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.security.PinSettingsSection
import com.aistudio.dhanam.expense.agent.learning.AgentLearningSection
import com.aistudio.dhanam.expense.reminders.FinanceReminderSection
import com.aistudio.dhanam.expense.ui.components.FinanceDataSection
import com.aistudio.dhanam.expense.ui.components.LegacyMigrationSection
import com.aistudio.dhanam.expense.ui.components.ModelManagerSection
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import com.aistudio.dhanam.expense.ui.theme.MutedText

@Composable
fun SettingsScreen(inferenceViewModel: LocalInferenceViewModel) {
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
                color = MutedText
            )
        }
    }
}
