package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.ui.theme.MutedText

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Privacy and application status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsInfoCard(
            title = "Network access",
            detail = "The Phase 1 manifest does not request INTERNET permission. Explicit model downloads are a later phase and require a separate privacy review."
        )
        SettingsInfoCard(
            title = "On-device AI",
            detail = "No model is bundled, downloaded, or run by this foundation. Model selection and resource eligibility belong to Phase 3."
        )
        SettingsInfoCard(
            title = "Security",
            detail = "PIN, biometrics, auto-lock and data export controls will be implemented only after the legacy security behavior is audited."
        )
        SettingsInfoCard(title = "Version", detail = "Dhanam AI 0.1.0 · Phase 1 foundation")
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
