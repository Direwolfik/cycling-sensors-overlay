package cz.novotny.cyclingsensorsoverlay.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.novotny.cyclingsensorsoverlay.ui.components.DebugLogDialog
import cz.novotny.cyclingsensorsoverlay.ui.components.MainBottomBar
import cz.novotny.cyclingsensorsoverlay.ui.components.MainTopAppBar
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardScreen
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import cz.novotny.cyclingsensorsoverlay.ui.navigation.DashboardRoute
import cz.novotny.cyclingsensorsoverlay.ui.navigation.ScannerRoute
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerScreen
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        DebugLogDialog(
            onDismissRequest = { showConfirmDialog = false },
            onConfirm = {
                showConfirmDialog = false
                AppLogger.sendManualDebugSnapshot(context, "Triggered from TopAppBar")
            }
        )
    }

    Scaffold(
        topBar = {
            MainTopAppBar(
                currentRoute = currentRoute,
                onDebugClick = { showConfirmDialog = true }
            )
        },
        bottomBar = {
            MainBottomBar(
                currentRoute = currentRoute,
                onNavigateToDashboard = {
                    if (currentRoute !is DashboardRoute) {
                        backStack.clear()
                        backStack.add(DashboardRoute)
                    }
                },
                onNavigateToScanner = {
                    if (currentRoute !is ScannerRoute) {
                        backStack.clear()
                        backStack.add(ScannerRoute)
                    }
                }
            )
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding)
        ) { key ->
            when (key) {
                is DashboardRoute -> NavEntry(key) {
                    val dashboardViewModel: DashboardViewModel = koinViewModel()
                    DashboardScreen(viewModel = dashboardViewModel)
                }
                is ScannerRoute -> NavEntry(key) {
                    val scannerViewModel: ScannerViewModel = koinViewModel()
                    ScannerScreen(viewModel = scannerViewModel)
                }
                else -> NavEntry(key) {}
            }
        }
    }
}
