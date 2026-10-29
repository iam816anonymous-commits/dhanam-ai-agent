package com.aistudio.dhanam.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aistudio.dhanam.expense.ui.DhanamApp
import com.aistudio.dhanam.expense.ui.theme.DhanamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DhanamTheme { DhanamApp() } }
    }
}
