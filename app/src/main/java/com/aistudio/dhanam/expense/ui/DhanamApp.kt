package com.aistudio.dhanam.expense.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import android.app.Application
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.dhanam.expense.agent.AgentViewModel
import com.aistudio.dhanam.expense.model.LocalInferenceRuntimeRegistry
import com.aistudio.dhanam.expense.ui.screens.AgentScreen
import com.aistudio.dhanam.expense.ui.screens.ExpensesScreen
import com.aistudio.dhanam.expense.ui.screens.InsightsScreen
import com.aistudio.dhanam.expense.ui.screens.KhataScreen
import com.aistudio.dhanam.expense.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhanamApp(
    agentViewModel: AgentViewModel = viewModel(),
    isDarkTheme: Boolean = true,
    onThemeChange: (Boolean) -> Unit = {},
    onLockClick: () -> Unit = {},
    canLock: Boolean = false
) {
    val context = LocalContext.current
    val inferenceViewModel = remember(context) {
        LocalInferenceRuntimeRegistry.getOrCreate(context.applicationContext as Application)
    }
    agentViewModel.attachLocalInference(inferenceViewModel)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = AppDestination.entries.firstOrNull { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    } ?: AppDestination.AGENT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        currentTab.label.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = { onThemeChange(!isDarkTheme) }) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (canLock) {
                        IconButton(onClick = onLockClick) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock app",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                AppDestination.entries.forEach { destination ->
                    val selected = currentTab == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = {
                            Text(
                                destination.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = AppDestination.AGENT.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.AGENT.route) { AgentScreen(agentViewModel) }
            composable(AppDestination.EXPENSES.route) { ExpensesScreen() }
            composable(AppDestination.KHATA.route) { KhataScreen() }
            composable(AppDestination.INSIGHTS.route) { InsightsScreen() }
            composable(AppDestination.SETTINGS.route) { SettingsScreen(inferenceViewModel, isDarkTheme, onThemeChange) }
        }
    }
}
