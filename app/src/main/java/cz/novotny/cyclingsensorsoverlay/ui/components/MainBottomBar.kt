package cz.novotny.cyclingsensorsoverlay.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import cz.novotny.cyclingsensorsoverlay.ui.navigation.DashboardRoute
import cz.novotny.cyclingsensorsoverlay.ui.navigation.ScannerRoute

@Composable
fun MainBottomBar(
    currentRoute: NavKey?,
    onNavigateToDashboard: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute is DashboardRoute,
            onClick = onNavigateToDashboard,
            icon = { Icon(Icons.Default.Speed, contentDescription = "Dashboard") },
            label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = currentRoute is ScannerRoute,
            onClick = onNavigateToScanner,
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = "Sensors") },
            label = { Text("Sensors") }
        )
    }
}
