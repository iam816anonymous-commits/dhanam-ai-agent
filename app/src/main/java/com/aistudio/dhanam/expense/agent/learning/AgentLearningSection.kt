package com.aistudio.dhanam.expense.agent.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    val scope = rememberCoroutineScope()

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
                    "Android may delay or skip background work because of Doze, battery restrictions or force-stop. Nightly work analyses local finance records and proposes tested exact-description skills. If the app's shared model runtime is already loaded in the same process, the intelligence and skill agents also use it sequentially. If it is unavailable, deterministic work still runs. No model weights are retrained and no skill is activated without your approval.",
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
                                scope.launch { db.financeDao().approveAgentSkill(skill.id, System.currentTimeMillis()) }
                            }, modifier = Modifier.weight(1f)) { Text("Approve skill") }
                            OutlinedButton(onClick = {
                                scope.launch { db.financeDao().rejectAgentSkill(skill.id) }
                            }, modifier = Modifier.weight(1f)) { Text("Reject") }
                        }
                    }
                }
            }
        }
        val activeCount = skills.count { it.status == "ACTIVE" }
        Text("Active approved skills: $activeCount", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)


        val activeSkills = skills.filter { it.status == "ACTIVE" }
        if (activeSkills.isNotEmpty()) {
            Text("Active learned rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            activeSkills.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(skill.title, fontWeight = FontWeight.Medium)
                            Text("“${skill.matchText}” → ${skill.category}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            scope.launch { db.financeDao().deactivateAgentSkill(skill.id) }
                        }) { Text("Disable") }
                    }
                }
            }
        }
        val disabledSkills = skills.filter { it.status == "DISABLED" }
        if (disabledSkills.isNotEmpty()) {
            Text("Disabled rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            disabledSkills.forEach { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(skill.title, fontWeight = FontWeight.Medium)
                            Text("“${skill.matchText}” → ${skill.category}", style = MaterialTheme.typography.bodySmall)
                            Text("Validated ${skill.testPassCount}/${skill.testTotalCount}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = {
                            scope.launch { db.financeDao().reactivateAgentSkill(skill.id, System.currentTimeMillis()) }
                        }) { Text("Restore") }
                    }
                }
            }
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
    }
}
