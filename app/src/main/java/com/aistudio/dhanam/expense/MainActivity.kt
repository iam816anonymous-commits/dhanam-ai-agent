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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchWakeCommand(intent)
        applyScreenshotPrivacy()
        val uiPreferences = getSharedPreferences("dhanam_ui", MODE_PRIVATE)
        setContent {
            var darkTheme by remember { mutableStateOf(uiPreferences.getBoolean("dark_theme", true)) }
            DhanamTheme(darkTheme = darkTheme) {
                AppLockGate { onLock, canLock ->
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
