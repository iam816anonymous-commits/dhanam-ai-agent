package com.aistudio.dhanam.expense.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compatibility wrapper: all voice entry points share AgentVoiceControl's recognizer lifecycle. */
@Deprecated("Use AgentVoiceControl; voice recognition is centralised.")
@Composable
@Suppress("UNUSED_PARAMETER")
fun OfflineVoiceInput(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    showLanguageSelector: Boolean = false,
    showStatus: Boolean = false,
    modifier: Modifier = Modifier
) {
    AgentVoiceControl(enabled = enabled, onRecognized = { _, translated -> onRecognized(translated) }, modifier = modifier)
}
