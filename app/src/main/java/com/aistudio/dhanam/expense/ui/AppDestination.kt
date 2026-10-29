package com.aistudio.dhanam.expense.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    AGENT("agent", "Agent", Icons.Default.AutoAwesome),
    EXPENSES("expenses", "Expenses", Icons.AutoMirrored.Filled.ReceiptLong),
    KHATA("khata", "Khata", Icons.Default.Handshake),
    INSIGHTS("insights", "Insights", Icons.Default.Insights),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}
