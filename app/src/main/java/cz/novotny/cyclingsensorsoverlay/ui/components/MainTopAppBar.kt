package cz.novotny.cyclingsensorsoverlay.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavKey
import cz.novotny.cyclingsensorsoverlay.ui.navigation.DashboardRoute
import cz.novotny.cyclingsensorsoverlay.ui.navigation.ScannerRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    currentRoute: NavKey?,
    onDebugClick: () -> Unit
) {
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
        actions = {
            IconButton(onClick = onDebugClick) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Send Debug Logs to Firebase",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}
