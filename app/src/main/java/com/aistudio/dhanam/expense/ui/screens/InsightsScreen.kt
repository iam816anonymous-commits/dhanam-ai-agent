package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.ui.components.FeatureEmptyState

@Composable
fun InsightsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Insights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Understand patterns from your real records", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeatureEmptyState(
            title = "Insights need financial data",
            message = "Charts and spending summaries will be enabled only after local finance records and deterministic calculations are implemented. No invented totals or forecasts are shown."
        )
    }
}
