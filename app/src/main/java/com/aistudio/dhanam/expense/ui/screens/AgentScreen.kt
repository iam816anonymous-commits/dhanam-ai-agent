package com.aistudio.dhanam.expense.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.dhanam.expense.agent.AgentViewModel
import com.aistudio.dhanam.expense.agent.PlannedFinanceAction
import com.aistudio.dhanam.expense.ui.components.OfflineVoiceInput

private data class QuickCommand(val label: String, val prompt: String)

@Composable
fun AgentScreen(viewModel: AgentViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    val quickCommands = remember {
        listOf(
            QuickCommand("＋ Expense", "I spent ₹250 on lunch"),
            QuickCommand("◷ Budget", "set food budget to 3000"),
            QuickCommand("↩ Repayment", "record repayment 500 from Ravi"),
            QuickCommand("📊 Spending analysis", "How can I reduce my expenses?"),
            QuickCommand("♙ Khata summary", "who owes me money?"),
            QuickCommand("✦ Help", "help")
        )
    }

    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(7.dp).background(
                        when (state.activity) {
                            "error" -> MaterialTheme.colorScheme.error
                            "thinking", "acting", "analyzing" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }, CircleShape
                    ))
                    Text(
                        when (state.activity) {
                            "thinking" -> "THINKING"
                            "analyzing" -> "ANALYZING SPENDING"
                            "acting" -> "VERIFYING"
                            "awaiting_confirmation" -> "YOUR APPROVAL"
                            "complete" -> "VERIFIED"
                            "error" -> "NEEDS ATTENTION"
                            else -> "DHANAM READY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = viewModel::clearChat, enabled = !state.busy) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear conversation", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AgentHeroCard(activity = state.activity, status = state.status)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(quickCommands, key = { it.label }) { command ->
                    FilterChip(
                        selected = false,
                        onClick = { input = command.prompt },
                        label = { Text(command.label) },
                        enabled = !state.busy
                    )
                }
            }
        }

        if (state.pendingAction != null) {
            ActionReviewCard(
                action = state.pendingAction!!,
                enabled = !state.busy,
                onConfirm = viewModel::confirmPending,
                onCancel = viewModel::cancelPending
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                val fromUser = message.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(if (fromUser) 0.88f else 0.96f),
                        shape = RoundedCornerShape(
                            topStart = 17.dp, topEnd = 17.dp,
                            bottomStart = if (fromUser) 17.dp else 5.dp,
                            bottomEnd = if (fromUser) 5.dp else 17.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (fromUser) "YOU" else "DHANAM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        state.status?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = if (state.activity == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(1000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message Dhanam") },
            placeholder = { Text("I spent ₹250 on lunch…") },
            minLines = 1,
            maxLines = 3,
            enabled = !state.busy,
            shape = RoundedCornerShape(18.dp)
        )
        OfflineVoiceInput(
            enabled = !state.busy,
            onRecognized = { spoken ->
                // Match the reference app: a completed voice transcript is sent to the
                // finance agent automatically. The agent still stages a preview and asks
                // for confirmation before any database write.
                input = spoken.take(1000)
                viewModel.submit(spoken)
                input = ""
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { viewModel.submit("help") },
                enabled = !state.busy,
                modifier = Modifier.weight(0.38f)
            ) { Text("Examples") }
            Button(
                onClick = { viewModel.submit(input); input = "" },
                enabled = input.isNotBlank() && !state.busy,
                modifier = Modifier.weight(0.62f),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (state.busy) "Working…" else "Send to Dhanam") }
        }
        Text(
            "Private by design · Local database · Financial changes require confirmation",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AgentHeroCard(activity: String, status: String?) {
    val transition = rememberInfiniteTransition(label = "dhanam-hero")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (activity == "thinking" || activity == "acting" || activity == "analyzing") 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (activity == "acting") 500 else if (activity == "analyzing") 700 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val label = when (activity) {
        "thinking" -> "THINKING & UNDERSTANDING"
        "acting" -> "CHECKING LOCAL RECORDS"
        "analyzing" -> "ANALYZING YOUR SPENDING"
        "awaiting_confirmation" -> "READY FOR YOUR APPROVAL"
        "complete" -> "ACTION VERIFIED"
        "error" -> "ACTION NEEDS ATTENTION"
        else -> "YOUR PRIVATE FINANCE COMPANION"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier.size(62.dp).scale(pulse).graphicsLayer { alpha = if (activity == "error") 0.88f else 1f }
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(29.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DHANAM AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Text(
                    status ?: "Expenses · Budgets · Khata repayments · Offline voice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun ActionReviewCard(
    action: PlannedFinanceAction,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REVIEW BEFORE SAVING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(action.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Nothing has been saved yet. Check the details, then confirm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Confirm & save") }
                OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}
