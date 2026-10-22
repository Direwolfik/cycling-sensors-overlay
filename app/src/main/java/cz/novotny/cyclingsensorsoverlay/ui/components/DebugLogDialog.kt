package cz.novotny.cyclingsensorsoverlay.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cz.novotny.cyclingsensorsoverlay.util.AppLogger

@Composable
fun DebugLogDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Send Debug Logs?") },
        text = { Text("Upload live BLE & Radar telemetry snapshot directly to Firebase Crashlytics and copy logs to clipboard?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Send Logs")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismissRequest()
                    AppLogger.shareDebugLogs(context, "Triggered from TopAppBar")
                }) {
                    Text("Share...")
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )
}
