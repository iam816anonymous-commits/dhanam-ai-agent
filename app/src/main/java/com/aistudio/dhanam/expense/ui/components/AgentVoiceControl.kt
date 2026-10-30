package com.aistudio.dhanam.expense.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Voice entry point using Android's system RecognizerIntent activity.
 * This deliberately avoids owning a SpeechRecognizer instance in the overlay,
 * preventing recognizer lifecycle leaks when the floating assistant is recomposed.
 * EXTRA_PREFER_OFFLINE is a request to the installed speech service, not a guarantee.
 */
@Composable
fun AgentVoiceControl(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnRecognized by rememberUpdatedState(onRecognized)
    val preferences = remember(context) { context.getSharedPreferences("dhanam_voice", 0) }
    var language by remember(context) {
        mutableStateOf(preferences.getString("recognition_language", "en-IN") ?: "en-IN")
    }
    var showDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                showDialog = false
                status = null
                latestOnRecognized(spoken)
            } else {
                status = "No speech was captured. Check the speech service or try again."
                showDialog = true
            }
        } else {
            status = "Voice capture was cancelled. You can retry or type your request."
            showDialog = true
        }
    }

    fun launchSystemRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your expense, income, budget or Khata request")
            }
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            status = "No compatible Android speech-recognition app is installed. Type your request instead."
            showDialog = true
        } catch (error: Exception) {
            status = error.message ?: "Could not open Android speech recognition."
            showDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSystemRecognition()
        else status = "Microphone permission was denied. Nothing was recorded."
    }

    FilledTonalButton(
        onClick = { status = null; showDialog = true },
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(Icons.Default.Mic, contentDescription = null)
        Text("Speak", modifier = Modifier.padding(start = 5.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Voice input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose the language, then use the Android speech service. Offline recognition depends on the installed engine and downloaded language pack.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en-IN" to "English", "te-IN" to "తెలుగు", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                            FilterChip(
                                selected = language == code,
                                onClick = {
                                    language = code
                                    preferences.edit().putString("recognition_language", code).apply()
                                    status = null
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    status?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = enabled,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showDialog = false
                            launchSystemRecognition()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) { Text("Start listening") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}
