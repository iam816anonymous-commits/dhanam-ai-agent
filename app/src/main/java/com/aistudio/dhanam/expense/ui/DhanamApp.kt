package com.aistudio.dhanam.expense.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.dhanam.expense.agent.AgentViewModel
import com.aistudio.dhanam.expense.model.LocalInferenceViewModel
import com.aistudio.dhanam.expense.ui.components.LivingAgentOverlay
import com.aistudio.dhanam.expense.ui.screens.AgentScreen
import com.aistudio.dhanam.expense.ui.screens.ExpensesScreen
import com.aistudio.dhanam.expense.ui.screens.InsightsScreen
import com.aistudio.dhanam.expense.ui.screens.KhataScreen
import com.aistudio.dhanam.expense.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhanamApp(agentViewModel: AgentViewModel = viewModel()) {
    val inferenceViewModel: LocalInferenceViewModel = viewModel()
    agentViewModel.attachLocalInference(inferenceViewModel)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = AppDestination.entries.firstOrNull { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    } ?: AppDestination.AGENT
    val agentState by agentViewModel.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("DHANAM", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary)
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentTab == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = AppDestination.AGENT.route, modifier = Modifier.padding(innerPadding)) {
                composable(AppDestination.AGENT.route) { AgentScreen(agentViewModel) }
                composable(AppDestination.EXPENSES.route) { ExpensesScreen() }
                composable(AppDestination.KHATA.route) { KhataScreen() }
                composable(AppDestination.INSIGHTS.route) { InsightsScreen() }
                composable(AppDestination.SETTINGS.route) { SettingsScreen(inferenceViewModel) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 82.dp)) {
            LivingAgentOverlay(activity = agentState.activity, status = agentState.status) {
                navController.navigate(AppDestination.AGENT.route) { launchSingleTop = true }
            }
        }
    }
}
