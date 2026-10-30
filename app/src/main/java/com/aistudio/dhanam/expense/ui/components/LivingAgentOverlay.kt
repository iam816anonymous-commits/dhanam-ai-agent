package com.aistudio.dhanam.expense.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.ui.AppDestination

/**
 * In-app floating assistant. It is composed above the NavHost, so it remains available
 * on every Dhanam destination. It provides real navigation entry points; finance actions
 * are handled in the shared AgentViewModel and still require explicit confirmation.
 */
@Composable
fun LivingAgentOverlay(
    activity: String,
    status: String?,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "living-agent")
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (activity == "acting" || activity == "thinking") 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (activity == "acting") 430 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (expanded) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("DHANAM ASSISTANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when (activity) {
                            "thinking" -> "Understanding your request…"
                            "acting" -> "Validating a finance action…"
                            "awaiting_confirmation" -> "Waiting for your approval"
                            "complete" -> "Last action verified"
                            "error" -> "Action needs attention"
                            else -> status ?: "Ready across this app"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onNavigate(AppDestination.AGENT); expanded = false }) { Text("Open assistant") }
                    TextButton(onClick = { onNavigate(AppDestination.EXPENSES); expanded = false }) { Text("Expenses") }
                    TextButton(onClick = { onNavigate(AppDestination.KHATA); expanded = false }) { Text("Khata") }
                    TextButton(onClick = { onNavigate(AppDestination.INSIGHTS); expanded = false }) { Text("Insights") }
                    TextButton(onClick = { onNavigate(AppDestination.SETTINGS); expanded = false }) { Text("Settings and models") }
                }
            }
        } else if (activity != "idle" || status != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (activity) {
                            "thinking" -> "Thinking…"
                            "acting" -> "Checking the change…"
                            "awaiting_confirmation" -> "Ready for your approval"
                            "complete" -> "Action verified"
                            "error" -> "Needs attention"
                            else -> status ?: "Dhanam assistant"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(4.dp)
                .size(62.dp)
                .graphicsLayer { translationY = bob }
                .scale(pulse)
                .shadow(12.dp, CircleShape)
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                    CircleShape
                )
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = if (expanded) "Close Dhanam assistant menu" else "Open Dhanam living assistant",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
