package com.aistudio.dhanam.expense.agent.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.data.FinanceDatabase
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AgentLearningSection() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("agent_learning", 0) }
    var enabled by remember(context) {
        mutableStateOf(preferences.getBoolean(NightlyAgentLearningScheduler.ENABLED_PREFERENCE, false))
    }
    val db = remember(context) { FinanceDatabase.getInstance(context) }
    val reports by db.financeDao().observeAgentLearningReports().collectAsState(initial = emptyList())
    val skills by db.financeDao().observeAgentSkills().collectAsState(initial = emptyList())
    val memories by db.financeDao().observeAgentMemories().collectAsState(initial = emptyList())
    val messages by db.financeDao().observeAgentMessages().collectAsState(initial = emptyList())
    val recommendationFeedback by db.financeDao().observeRecommendationFeedback().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var skillActionMessage by remember { mutableStateOf<String?>(null) }
    var confirmClearMemory by remember { mutableStateOf(false) }
    var memoryClearMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enabled) {
        if (enabled) NightlyAgentLearningScheduler.scheduleNext(context)
        else NightlyAgentLearningScheduler.disable(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Nightly agent preparation", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Schedule local finance checks between 1 AM and 6 AM when Android permits background work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            preferences.edit().putBoolean(NightlyAgentLearningScheduler.ENABLED_PREFERENCE, value).apply()
                            enabled = value
                        }
                    )
                }
                Text(
                    "Android may delay or skip background work because of Doze, battery restrictions or force-stop. Nightly work analyses local finance records and proposes tested exact-description skills. When the verified preferred model is compatible and resources/time permit, the worker loads it and runs all three agents sequentially through the same runtime. If it is unavailable, deterministic work still runs. No model weights are retrained and no skill is activated without your approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Learned skill proposals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val proposedSkills = skills.filter { it.status == "PROPOSED" }
        if (proposedSkills.isEmpty()) {
            Text("No pending skill approvals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            proposedSkills.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(skill.title, fontWeight = FontWeight.SemiBold)
                        Text("Match: “${skill.matchText}” → ${skill.category}")
                        Text(
                            "Validated on ${skill.testPassCount}/${skill.testTotalCount} held-out examples from ${skill.exampleCount} local records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        val updated = db.financeDao().approveAgentSkill(skill.id, System.currentTimeMillis())
                                        skillActionMessage = if (updated == 1) "Approved ${skill.title} version ${skill.version}."
                                            else "This skill proposal is no longer eligible for approval."
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        skillActionMessage = error.message ?: "Could not approve this skill."
                                    }
                                }
                            }, modifier = Modifier.weight(1f)) { Text("Approve skill") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        val updated = db.financeDao().rejectAgentSkill(skill.id)
                                        skillActionMessage = if (updated == 1) "Rejected ${skill.title}." else "This proposal was already resolved."
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        skillActionMessage = error.message ?: "Could not reject this skill."
                                    }
                                }
                            }, modifier = Modifier.weight(1f)) { Text("Reject") }
                        }
                    }
                }
            }
        }
        val activeCount = skills.count { it.status == "ACTIVE" }
        Text("Active approved skills: $activeCount", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(
            "Recommendation feedback: ${recommendationFeedback.count { it.decision == "HELPFUL" }} helpful · ${recommendationFeedback.count { it.decision == "NOT_USEFUL" }} not useful",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val activeSkills = skills.filter { it.status == "ACTIVE" }
        activeSkills.forEach { skill ->
            val hasPreviousVersion = skills.any {
                it.skillKey == skill.skillKey && it.version < skill.version && it.status == "SUPERSEDED"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(skill.title, fontWeight = FontWeight.SemiBold)
                    Text("Active version ${skill.version} · ${skill.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hasPreviousVersion) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    skillActionMessage = if (db.financeDao().rollbackAgentSkill(skill.skillKey, System.currentTimeMillis())) {
                                        "Rolled back ${skill.title} to its previous validated version."
                                    } else {
                                        "No previous validated version was available."
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    skillActionMessage = error.message ?: "Could not roll back this skill."
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Roll back to previous version") }
                    } else {
                        Text("No previous approved version is available to restore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (skillActionMessage != null) {
            Text(skillActionMessage.orEmpty(), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { skillActionMessage = null }) { Text("Dismiss") }
        }

        Text("Shared agent memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Each specialist keeps a bounded local summary of its latest verified work. Memories are historical context, not a source of current financial balances.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (memories.isEmpty()) {
            Text("Agent memories will appear after the first nightly run.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            memories.forEach { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(memory.agentRole.replace("_", " "), fontWeight = FontWeight.SemiBold)
                        Text(memory.memoryKey.replace("_", " "), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary)
                        Text(memory.memoryValue, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Evidence date: ${LocalDate.ofEpochDay(memory.sourceReportEpochDay)} · Confidence: ${memory.confidencePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text("Agent coordination history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (messages.isEmpty()) {
            Text("No inter-agent handoffs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            messages.take(12).forEach { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${message.fromAgent.replace("_", " ")} → ${message.toAgent.replace("_", " ")}",
                            fontWeight = FontWeight.SemiBold)
                        Text(message.messageType.replace("_", " "), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Text(message.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Agent learning history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (reports.isEmpty()) {
            Text("No nightly report yet. Enable the schedule and allow an eligible night window.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            reports.take(7).forEach { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            LocalDate.ofEpochDay(report.reportDateEpochDay).toString(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Status: ${report.runStatus}", color = MaterialTheme.colorScheme.secondary)
                        Text("Finance operations", fontWeight = FontWeight.Medium)
                        Text(report.operationsSummary, style = MaterialTheme.typography.bodySmall)
                        Text("Financial intelligence", fontWeight = FontWeight.Medium)
                        Text(report.intelligenceSummary, style = MaterialTheme.typography.bodySmall)
                        Text("Skill improvement", fontWeight = FontWeight.Medium)
                        Text(report.skillSummary, style = MaterialTheme.typography.bodySmall)
                        Text("Next improvements", fontWeight = FontWeight.Medium)
                        Text(report.nextImprovements, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Preparation duration: ${report.durationMillis} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { confirmClearMemory = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Clear agent memory and handoff history") }
        if (memoryClearMessage != null) {
            Text(memoryClearMessage.orEmpty(), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { memoryClearMessage = null }) { Text("Dismiss") }
        }

    }

    if (confirmClearMemory) {
        AlertDialog(
            onDismissRequest = { confirmClearMemory = false },
            title = { Text("Clear agent memory?") },
            text = {
                Text("This deletes the specialists' saved summaries and handoff history only. Expenses, income, budgets, Khata records, approved skills, recommendation feedback and daily learning reports are not deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            db.financeDao().clearAgentMemoryAndHandoffs()
                            memoryClearMessage = "Agent memories and handoff history cleared. Financial records and learning reports were preserved."
                            confirmClearMemory = false
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            memoryClearMessage = error.message ?: "Could not clear agent memory."
                            confirmClearMemory = false
                        }
                    }
                }) { Text("Clear memory") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMemory = false }) { Text("Cancel") }
            }
        )
    }
}
