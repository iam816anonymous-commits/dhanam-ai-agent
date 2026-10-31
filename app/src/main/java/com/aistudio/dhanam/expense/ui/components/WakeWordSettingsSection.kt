package com.aistudio.dhanam.expense.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aistudio.dhanam.expense.voice.WakeWordModelInstaller
import com.aistudio.dhanam.expense.voice.WakeWordSecrets
import com.aistudio.dhanam.expense.voice.WakeWordService
import kotlinx.coroutines.launch

/** Explicit opt-in setup for the persistent, local Hey Jarvis wake-word service. */
@Composable
fun WakeWordSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) {
        context.getSharedPreferences(WakeWordService.PREFS, 0)
    }
    var accessKey by remember { mutableStateOf("") }
    var modelReady by remember(context) {
        mutableStateOf(
            WakeWordModelInstaller.modelFile(context).let { it.isFile && it.length() > 0L } &&
                !WakeWordSecrets.read(context).isNullOrBlank()
        )
    }
    var listening by remember(context) { mutableStateOf(preferences.getBoolean(WakeWordService.KEY_ENABLED, false)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Wake-word listening is off.") }
    var pendingStart by remember { mutableStateOf(false) }

    fun startService() {
        preferences.edit().putBoolean(WakeWordService.KEY_ENABLED, true).apply()
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
            )
            listening = true
            status = "Hey Jarvis is starting. Keep the persistent notification enabled."
        } catch (error: Exception) {
            listening = false
            preferences.edit().putBoolean(WakeWordService.KEY_ENABLED, false).apply()
            status = "Could not start microphone service: ${error.message ?: "Android blocked the service"}"
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStart) startService()
        else if (!granted) status = "Microphone permission is required for wake-word detection."
        pendingStart = false
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) status = "Notification permission was denied. Android may hide the persistent listening notification."
        if (pendingStart) {
            val microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (microphoneGranted) {
                startService()
                pendingStart = false
            } else {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hey Jarvis · hands-free assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Say “Hey Jarvis”, wait for the short beep, then speak your command. Dhanam converts speech to text and routes it through the existing finance agent. The finance LLM is not used for wake-word detection or speech recognition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!modelReady) {
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Picovoice AccessKey") },
                    placeholder = { Text("Paste your AccessKey") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Text(
                    "A one-time AccessKey is needed to generate the custom “Hey Jarvis” keyword model. Create it in the Picovoice Console. The key is encrypted with Android Keystore and is not included in the app source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        busy = true
                        status = "Generating the Hey Jarvis keyword model. Internet is needed for this one-time setup…"
                        scope.launch {
                            try {
                                WakeWordSecrets.save(context, accessKey)
                                val model = WakeWordModelInstaller.prepare(context, accessKey)
                                modelReady = model.isFile && model.length() > 0L
                                accessKey = ""
                                status = "Hey Jarvis model is ready. Enable listening below."
                            } catch (error: Exception) {
                                status = "Could not prepare wake word: ${error.message ?: "check AccessKey and connection"}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = accessKey.isNotBlank() && !busy
                ) { Text(if (busy) "Preparing…" else "Prepare Hey Jarvis") }
            } else {
                Text("Wake-word model: installed · Hey Jarvis", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Always listen for Hey Jarvis", fontWeight = FontWeight.Medium)
                    Text(
                        "Runs as a visible microphone foreground service while enabled, including when the screen is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = listening,
                    enabled = modelReady && !busy,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            pendingStart = true
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startService()
                                    pendingStart = false
                                } else {
                                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        } else {
                            preferences.edit().putBoolean(WakeWordService.KEY_ENABLED, false).apply()
                            context.stopService(Intent(context, WakeWordService::class.java))
                            listening = false
                            status = "Hey Jarvis listening stopped."
                        }
                    }
                )
            }
            Text(
                when {
                    Build.VERSION.SDK_INT >= 34 -> "Android 14+: enable listening while Dhanam is visible. Android requires microphone foreground-service permission and restricts starting it from the background."
                    Build.VERSION.SDK_INT >= 26 -> "Android 8.1+: a persistent foreground-service notification is shown while listening is enabled."
                    else -> "This Android version is not supported for background wake-word listening."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Wake-word detection runs locally after setup. Speech-to-text uses the selected Android speech provider and may need its own offline language pack. On Android 8.1, offline recognition is not guaranteed by the platform API. No expense is saved until you review and confirm it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
