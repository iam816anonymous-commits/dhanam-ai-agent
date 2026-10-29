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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun LivingAgentOverlay(activity: String, status: String?, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "living-agent")
    val bob by transition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (activity == "acting" || activity == "thinking") 1.13f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (activity == "acting") 430 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (activity != "idle" || status != null) {
            Surface(
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp, shadowElevation = 8.dp, modifier = Modifier.clickable(onClick = onClick)
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
            modifier = Modifier.padding(4.dp).size(62.dp).graphicsLayer { translationY = bob }.scale(pulse).shadow(12.dp, CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "Open Dhanam living assistant",
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}
