package com.aistudio.dhanam.expense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography

private val DhanamDarkColors = darkColorScheme(
    primary = Gold400,
    onPrimary = Emerald950,
    primaryContainer = Emerald700,
    onPrimaryContainer = Ivory,
    secondary = Emerald400,
    onSecondary = Emerald950,
    secondaryContainer = Emerald800,
    onSecondaryContainer = Ivory,
    background = Emerald950,
    onBackground = Ivory,
    surface = Emerald900,
    onSurface = Ivory,
    surfaceVariant = CardGreen,
    onSurfaceVariant = MutedText,
    outline = OutlineGreen,
    error = Danger,
    onError = Emerald950
)

@Composable
fun DhanamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DhanamDarkColors, typography = Typography(), content = content)
}
