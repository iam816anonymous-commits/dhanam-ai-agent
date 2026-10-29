package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.dhanam.expense.ui.theme.Gold300
import com.aistudio.dhanam.expense.ui.theme.MutedText

@Composable
fun AgentScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Your money, understood.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "A private financial companion designed to help you understand expenses, budgets and lending.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedText
                )
                Spacer(Modifier.height(18.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(
                        "FOUNDATION IN PROGRESS",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold300,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        Text("Built around your privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        InfoCard(
            title = "Local-first by design",
            description = "Financial records are intended to stay on this device. This foundation makes no network requests."
        )
        InfoCard(
            title = "Actions will be verified",
            description = "The agent brain is not connected yet. Future actions will use validated finance tools and explicit confirmations where required."
        )
        InfoCard(
            title = "No sample money data",
            description = "Balances and transactions will appear only after real finance storage is implemented. This screen does not fabricate totals."
        )
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        }
    }
}
