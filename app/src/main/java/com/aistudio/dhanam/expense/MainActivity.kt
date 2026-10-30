package com.aistudio.dhanam.expense

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.aistudio.dhanam.expense.security.AppLockGate
import com.aistudio.dhanam.expense.ui.DhanamApp
import com.aistudio.dhanam.expense.ui.theme.DhanamTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onResume() {
        super.onResume()
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
