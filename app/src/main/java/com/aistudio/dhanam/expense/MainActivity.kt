package com.aistudio.dhanam.expense

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.setContent
import com.aistudio.dhanam.expense.security.AppLockGate
import com.aistudio.dhanam.expense.ui.DhanamApp
import com.aistudio.dhanam.expense.ui.theme.DhanamTheme
import com.aistudio.dhanam.expense.voice.WakeCommandBus
import com.aistudio.dhanam.expense.voice.WakeWordService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchWakeCommand(intent)
        applyScreenshotPrivacy()
        val uiPreferences = getSharedPreferences("dhanam_ui", MODE_PRIVATE)
        val dhanamApplication = application as DhanamApplication
        setContent {
            var darkTheme by remember { mutableStateOf(uiPreferences.getBoolean("dark_theme", true)) }
            var databaseReady by remember { mutableStateOf(false) }
            var databaseError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(dhanamApplication) {
                try {
                    dhanamApplication.databaseInitialization.await()
                    databaseReady = true
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    databaseError = error.message?.take(180) ?: "Database initialization failed."
                } catch (error: LinkageError) {
                    databaseError = error.message?.take(180) ?: "The encrypted database engine could not be loaded."
                }
            }

            DhanamTheme(darkTheme = darkTheme) {
                when {
                    databaseReady -> AppLockGate { onLock, canLock ->
                        DhanamApp(
                            isDarkTheme = darkTheme,
                            onThemeChange = { enabled ->
                                darkTheme = enabled
                                uiPreferences.edit().putBoolean("dark_theme", enabled).apply()
                            },
                            onLockClick = onLock,
                            canLock = canLock
                        )
                    }
                    else -> DatabaseStartupScreen(databaseError)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchWakeCommand(intent)
    }

    private fun restoreWakeWordServiceIfEnabled() {
        val preferences = getSharedPreferences(WakeWordService.PREFS, MODE_PRIVATE)
        if (!preferences.getBoolean(WakeWordService.KEY_ENABLED, false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            preferences.edit().putBoolean(WakeWordService.KEY_ENABLED, false).apply()
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
            )
        }.onFailure {
            preferences.edit().putBoolean(WakeWordService.KEY_ENABLED, false).apply()
        }
    }

    private fun dispatchWakeCommand(intent: android.content.Intent?) {
        if (intent?.action != WakeWordService.ACTION_OPEN_VOICE_COMMAND) return
        val transcript = intent.getStringExtra(WakeWordService.EXTRA_TRANSCRIPT).orEmpty()
        val language = intent.getStringExtra(WakeWordService.EXTRA_LANGUAGE) ?: "en-IN"
        WakeCommandBus.offer(transcript, language)
        intent.action = null
        intent.removeExtra(WakeWordService.EXTRA_TRANSCRIPT)
        intent.removeExtra(WakeWordService.EXTRA_LANGUAGE)
    }

    override fun onResume() {
        super.onResume()
        restoreWakeWordServiceIfEnabled()
        applyScreenshotPrivacy()
    }

    private fun applyScreenshotPrivacy() {
        val enabled = getSharedPreferences("dhanam_privacy", MODE_PRIVATE)
            .getBoolean("screenshot_privacy_enabled", false)
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}


@androidx.compose.runtime.Composable
private fun DatabaseStartupScreen(errorMessage: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (errorMessage == null) {
                CircularProgressIndicator()
                Text("Preparing your encrypted finance database…")
            } else {
                Text(
                    "Dhanam could not safely open your finance database.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Your existing database has not been intentionally deleted. Do not uninstall Dhanam or clear app storage. Resolve the database/key issue before continuing.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
