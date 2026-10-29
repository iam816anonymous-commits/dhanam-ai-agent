package com.aistudio.dhanam.expense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color

private val DhanamDarkColors = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = OnEmeraldPrimaryContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberSecondaryDark,
    secondaryContainer = AmberSecondaryContainerDark,
    onSecondaryContainer = OnAmberSecondaryContainerDark,
    tertiary = IndigoTertiaryDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = AlertRed,
    errorContainer = Color(0xFF7F1D1D),
    onError = Color.White,
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun DhanamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DhanamDarkColors, typography = Typography(), content = content)
}
