package cz.novotny.cyclingsensorsoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardScreen
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerScreen
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
data object DashboardRoute : NavKey

@Serializable
data object ScannerRoute : NavKey

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CyclingSensorsOverlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val backStack = rememberNavBackStack(DashboardRoute)
                    val currentRoute = backStack.lastOrNull()

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = when (currentRoute) {
                                            is DashboardRoute -> "Live Dashboard"
                                            is ScannerRoute -> "Sensors & Scanner"
                                            else -> "Cycling Sensors Overlay"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute is DashboardRoute,
                                    onClick = {
                                        if (currentRoute !is DashboardRoute) {
                                            backStack.clear()
                                            backStack.add(DashboardRoute)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Speed, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute is ScannerRoute,
                                    onClick = {
                                        if (currentRoute !is ScannerRoute) {
                                            backStack.clear()
                                            backStack.add(ScannerRoute)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Sensors") },
                                    label = { Text("Sensors") }
                                )
                            }
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
            }
        }
    }
}
