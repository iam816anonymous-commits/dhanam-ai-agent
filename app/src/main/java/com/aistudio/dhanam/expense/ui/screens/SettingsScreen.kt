package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.ui.components.ModelManagerSection
import com.aistudio.dhanam.expense.ui.theme.MutedText

@Composable
fun SettingsScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Privacy, application status and on-device AI", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsInfoCard(
            title = "Financial privacy",
            detail = "Financial records remain in local app storage. The app does not upload transactions, balances, prompts, or account data."
        )
        ModelManagerSection()
        SettingsInfoCard(
            title = "Runtime status",
            detail = "A verified model file and saved model preference do not mean inference is active. Runtime integration is blocked until dependency compatibility and memory/stability are tested on the target phone."
        )
        SettingsInfoCard(
            title = "Security",
            detail = "PIN, biometrics, auto-lock and legacy-data migration remain separate audited work; no unreviewed security control is implied."
        )
        SettingsInfoCard(title = "Version", detail = "Dhanam AI 0.3.0 · Phase 3 model manager foundation")
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
