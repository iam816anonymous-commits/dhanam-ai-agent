package com.aistudio.dhanam.expense.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.dhanam.expense.agent.AgentViewModel
import com.aistudio.dhanam.expense.model.LocalInferenceRuntimeRegistry
import com.aistudio.dhanam.expense.ui.components.AgentStatusPill
import com.aistudio.dhanam.expense.ui.components.JarvisCoreState
import com.aistudio.dhanam.expense.ui.components.LivingAgentOverlay
import com.aistudio.dhanam.expense.ui.components.OfflineVoiceInput
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
    val inferenceState by inferenceViewModel.state.collectAsState()
    val agentState by agentViewModel.state.collectAsState()
    agentViewModel.attachLocalInference(inferenceViewModel)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = AppDestination.entries.firstOrNull { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    } ?: AppDestination.AGENT

    fun navigateTo(destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        AgentStatusPill(
                            state = when (agentState.activity) {
                                "thinking", "analyzing" -> JarvisCoreState.THINKING
                                "acting" -> JarvisCoreState.PROCESSING
                                "awaiting_confirmation" -> JarvisCoreState.NOTING
                                "complete" -> JarvisCoreState.SPEAKING
                                else -> JarvisCoreState.IDLE
                            },
                            onClick = { navigateTo(AppDestination.AGENT) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    title = {
                        Text(
                            currentTab.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
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
                                Icon(Icons.Default.Lock, contentDescription = "Lock app", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                // Persistent voice control: available from Agent, Expenses, Khata, Insights and Settings.
                OfflineVoiceInput(
                    enabled = !agentState.busy,
                    onRecognized = { spoken ->
                        navigateTo(AppDestination.AGENT)
                        agentViewModel.submit(spoken)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                AppDestination.entries.forEach { destination ->
                    val selected = currentTab == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(destination) },
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
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.AGENT.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppDestination.AGENT.route) {
                    AgentScreen(
                        viewModel = agentViewModel,
                        isLocalModelReady = inferenceState.isReady,
                        onOpenSettings = { navigateTo(AppDestination.SETTINGS) }
                    )
                }
                composable(AppDestination.EXPENSES.route) { ExpensesScreen() }
                composable(AppDestination.KHATA.route) { KhataScreen() }
                composable(AppDestination.INSIGHTS.route) { InsightsScreen() }
                composable(AppDestination.SETTINGS.route) {
                    SettingsScreen(inferenceViewModel, isDarkTheme, onThemeChange)
                }
            }

            // The living assistant remains available above every destination, not only the Agent tab.
            LivingAgentOverlay(
                activity = agentState.activity,
                status = agentState.status,
                onNavigate = ::navigateTo,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}
