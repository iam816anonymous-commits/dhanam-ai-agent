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
fun ExpensesScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Expenses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your transaction history", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeatureEmptyState(
            title = "Finance storage is not connected yet",
            message = "Once Phase 2 adds the local database and transaction workflows, your real expenses will be shown here. No sample transactions are being displayed."
        )
    }
}
