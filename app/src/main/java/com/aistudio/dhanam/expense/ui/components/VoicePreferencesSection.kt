package com.aistudio.dhanam.expense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Persistent voice preferences shared by the floating microphone and Agent screen. */
@Composable
fun VoicePreferencesSection() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    var language by remember(context) {
        mutableStateOf(preferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    var voiceReplies by remember(context) {
        mutableStateOf(preferences.getBoolean("voice_feedback_enabled", false))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice and language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose the recognition language and whether Dhanam reads assistant replies aloud. Speech recognition uses the Android speech service; offline operation depends on its installed language packs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("en-IN" to "English", "te-IN" to "తెలుగు", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = {
                            language = code
                            preferences.edit().putString("recognition_language", code).apply()
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Speak assistant replies", fontWeight = FontWeight.Medium)
                    Text("Uses the phone's installed text-to-speech voice.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = voiceReplies,
                    onCheckedChange = {
                        voiceReplies = it
                        preferences.edit().putBoolean("voice_feedback_enabled", it).apply()
                    }
                )
            }
        }
    }
}
