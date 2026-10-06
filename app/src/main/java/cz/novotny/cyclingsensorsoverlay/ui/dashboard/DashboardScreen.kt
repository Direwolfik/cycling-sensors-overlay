package cz.novotny.cyclingsensorsoverlay.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.novotny.cyclingsensorsoverlay.domain.model.CombinedSensorState
import cz.novotny.cyclingsensorsoverlay.domain.model.ConnectionState
import cz.novotny.cyclingsensorsoverlay.domain.model.HeartRateData
import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.ui.overlay.RadarSideBarWidget
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val sensorState by viewModel.sensorState.collectAsState()
    val isOverlayRunning by viewModel.isOverlayRunning.collectAsState()
    val isRadarSimulated by viewModel.isRadarSimulated.collectAsState()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()
    val context = LocalContext.current

    DashboardContent(
        sensorState = sensorState,
        isOverlayRunning = isOverlayRunning,
        onToggleOverlay = { viewModel.toggleOverlay(context) },
        isRadarSimulated = isRadarSimulated,
        onToggleRadarSimulation = { viewModel.toggleRadarSimulation() },
        modifier = modifier
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(text = "System Overlay Permission Required")
            },
            text = {
                Text(text = "To show real-time sensor data over other apps, please grant the 'Display over other apps' permission in system settings.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.openOverlaySettings(context) }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissPermissionDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DashboardContent(
    sensorState: CombinedSensorState,
    isOverlayRunning: Boolean,
    onToggleOverlay: () -> Unit,
    isRadarSimulated: Boolean,
    onToggleRadarSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overlay Mode Switch Card
        OverlayToggleCard(
            isOverlayRunning = isOverlayRunning,
            onToggleOverlay = onToggleOverlay
        )

        // Power Dashboard Card (3s Power & Instantaneous Power)
        PowerCard(
            power3s = sensorState.power3sAverage,
            instantaneousPower = sensorState.powerData?.instantaneousPower
        )

        // Cadence and Heart Rate Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CadenceCard(
                cadence = sensorState.powerData?.cadence,
                modifier = Modifier.weight(1f)
            )
            HeartRateCard(
                bpm = sensorState.heartRateData?.bpm,
                modifier = Modifier.weight(1f)
            )
        }

        // Radar Threat Status Card with Embedded Vertical Side Radar Bar
        RadarCard(
            radarData = sensorState.radarData,
            isRadarSimulated = isRadarSimulated,
            onToggleRadarSimulation = onToggleRadarSimulation
        )

        // Sensor Slot Connection Status Card
        SensorConnectionStatusCard(connectionStates = sensorState.connectionStates)
    }
}

@Composable
private fun OverlayToggleCard(
    isOverlayRunning: Boolean,
    onToggleOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverlayRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isOverlayRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Overlay Mode",
                            tint = if (isOverlayRunning) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Column {
                    Text(
                        text = "Floating System Overlay",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isOverlayRunning) "Active - Telemetry & Radar widgets" else "Disabled - Tap to show overlay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isOverlayRunning,
                onCheckedChange = { onToggleOverlay() }
            )
        }
    }
}

@Composable
private fun PowerCard(
    power3s: Int?,
    instantaneousPower: Int?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Power",
                    tint = Color(0xFFFFB300)
                )
                Text(
                    text = "3-SECOND AVERAGE POWER",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = power3s?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 64.sp
                    ),
                    color = if (power3s != null) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "W",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Instantaneous:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = instantaneousPower?.let { "${it} W" } ?: "-- W",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CadenceCard(
    cadence: Int?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = "Cadence",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "CADENCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = cadence?.toString() ?: "--",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = if (cadence != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "RPM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HeartRateCard(
    bpm: Int?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart Rate",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "HEART RATE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = bpm?.toString() ?: "--",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = if (bpm != null) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RadarCard(
    radarData: RadarData?,
    isRadarSimulated: Boolean,
    onToggleRadarSimulation: () -> Unit
) {
    val activeThreats = radarData?.threats?.filter { it.threatLevel > 0 } ?: emptyList()
    val maxThreatLevel = activeThreats.maxOfOrNull { it.threatLevel } ?: 0
    val closestVehicle = activeThreats.minByOrNull { it.distanceMeters }

    val threatColor by animateColorAsState(
        targetValue = when (maxThreatLevel) {
            2 -> Color(0xFFD32F2F) // High Speed: Red
            1 -> Color(0xFFF57C00) // Approaching: Amber
            else -> Color(0xFF388E3C) // No Threat: Green
        },
        animationSpec = tween(300),
        label = "ThreatColor"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Radar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar",
                        tint = threatColor
                    )
                    Text(
                        text = "RADAR THREAT MONITOR",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = threatColor
                ) {
                    Text(
                        text = when (maxThreatLevel) {
                            2 -> "HIGH SPEED APPROACH"
                            1 -> "APPROACHING"
                            else -> "NO THREAT"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            // Main Radar Content: Left Metrics + Right Vertical Side Radar Line Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left metrics column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Approaching Vehicles",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${activeThreats.size} vehicle(s)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Column {
                        Text(
                            text = "Closest Vehicle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = closestVehicle?.let { "${it.distanceMeters.toInt()} m (${it.speedKmH.toInt()} km/h)" } ?: "--",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Simulation Test Switch Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isRadarSimulated) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Radar Test Simulation",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isRadarSimulated) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRadarSimulated) "Simulating vehicles 150m->0m" else "Tap to test vehicle dots",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isRadarSimulated) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isRadarSimulated,
                                onCheckedChange = { onToggleRadarSimulation() },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Embedded Vertical Radar Bar (Side Radar Visualization)
                RadarSideBarWidget(
                    radarData = radarData,
                    heightDp = 280,
                    widthDp = 64
                )
            }
        }
    }
}

@Composable
private fun SensorConnectionStatusCard(
    connectionStates: Map<SensorType, ConnectionState>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "SENSOR CONNECTION STATUS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorType.entries.forEach { type ->
                    val state = connectionStates[type] ?: ConnectionState.DISCONNECTED
                    SensorStatusChip(
                        type = type,
                        state = state,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorStatusChip(
    type: SensorType,
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor, statusIcon) = when (state) {
        ConnectionState.CONNECTED -> Triple("Connected", Color(0xFF388E3C), Icons.Default.CheckCircle)
        ConnectionState.CONNECTING -> Triple("Connecting", Color(0xFFF57C00), Icons.Default.Refresh)
        ConnectionState.DISCONNECTED -> Triple("Disconnected", Color(0xFF757575), Icons.Default.ErrorOutline)
        ConnectionState.ERROR -> Triple("Error", Color(0xFFD32F2F), Icons.Default.ErrorOutline)
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = when (type) {
                        SensorType.POWER -> "POWER"
                        SensorType.HEART_RATE -> "HR"
                        SensorType.RADAR -> "RADAR"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = statusColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardContentPreview() {
    CyclingSensorsOverlayTheme {
        DashboardContent(
            sensorState = CombinedSensorState(
                powerData = PowerData(instantaneousPower = 250, cadence = 88),
                power3sAverage = 245,
                heartRateData = HeartRateData(bpm = 152),
                radarData = RadarData(
                    threats = listOf(
                        RadarThreat(id = 1, threatLevel = 2, distanceMeters = 45f, speedKmH = 65f),
                        RadarThreat(id = 2, threatLevel = 1, distanceMeters = 110f, speedKmH = 40f)
                    )
                ),
                connectionStates = mapOf(
                    SensorType.POWER to ConnectionState.CONNECTED,
                    SensorType.HEART_RATE to ConnectionState.CONNECTED,
                    SensorType.RADAR to ConnectionState.CONNECTED
                )
            ),
            isOverlayRunning = true,
            onToggleOverlay = {},
            isRadarSimulated = true,
            onToggleRadarSimulation = {}
        )
    }
}
