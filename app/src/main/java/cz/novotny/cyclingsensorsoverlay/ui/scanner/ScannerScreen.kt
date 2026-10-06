package cz.novotny.cyclingsensorsoverlay.ui.scanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.novotny.cyclingsensorsoverlay.domain.model.ConnectionState
import cz.novotny.cyclingsensorsoverlay.domain.model.DiscoveredDevice
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme
import cz.novotny.cyclingsensorsoverlay.util.PermissionUtils
import cz.novotny.cyclingsensorsoverlay.util.PermissionsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val newState = PermissionUtils.checkPermissionsState(context)
        viewModel.updatePermissionsState(newState)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newState = PermissionUtils.checkPermissionsState(context)
                viewModel.updatePermissionsState(newState)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScannerScreenContent(
        uiState = uiState,
        onRequestBluetoothPermissions = {
            bluetoothPermissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions())
        },
        onRequestOverlayPermission = {
            PermissionUtils.openOverlaySettings(context)
        },
        onToggleScan = { viewModel.toggleScan() },
        onOpenPairingSheet = { slot -> viewModel.openPairingSheet(slot) },
        onClosePairingSheet = { viewModel.closePairingSheet() },
        onSetTargetSlot = { slot -> viewModel.setTargetSlot(slot) },
        onSetShowAllDevices = { showAll -> viewModel.setShowAllDevices(showAll) },
        onAssignDevice = { device, sensorType -> viewModel.assignDeviceToSlot(device, sensorType) },
        onClearSlot = { sensorType -> viewModel.clearSlotAssignment(sensorType) },
        onConnectSlot = { sensorType -> viewModel.connectSlot(sensorType) },
        onDisconnectSlot = { sensorType -> viewModel.disconnectSlot(sensorType) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenContent(
    uiState: ScannerUiState,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleScan: () -> Unit,
    onOpenPairingSheet: (SensorType) -> Unit,
    onClosePairingSheet: () -> Unit,
    onSetTargetSlot: (SensorType?) -> Unit,
    onSetShowAllDevices: (Boolean) -> Unit,
    onAssignDevice: (DiscoveredDevice, SensorType) -> Unit,
    onClearSlot: (SensorType) -> Unit,
    onConnectSlot: (SensorType) -> Unit,
    onDisconnectSlot: (SensorType) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cycling Sensors & Overlay",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BLE Sensor Scanner & Pairing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Section 1: Permissions Status Banners
            item {
                PermissionsSection(
                    permissionsState = uiState.permissionsState,
                    onRequestBluetoothPermissions = onRequestBluetoothPermissions,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }

            // Section 2: Sensor Slots with direct "Scan & Pair"
            item {
                Text(
                    text = "Sensor Slots",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            val slots = listOf(SensorType.POWER, SensorType.HEART_RATE, SensorType.RADAR)
            items(slots) { sensorType ->
                val slot = uiState.assignedSlots[sensorType] ?: SensorSlot(
                    slotId = sensorType.name.lowercase(),
                    sensorType = sensorType
                )
                val connectionState = uiState.connectionStates[sensorType] ?: ConnectionState.DISCONNECTED

                SensorSlotCard(
                    slot = slot,
                    connectionState = connectionState,
                    hasBluetoothPermission = uiState.permissionsState.hasBluetoothPermission,
                    onScanAndPair = { onOpenPairingSheet(sensorType) },
                    onConnect = { onConnectSlot(sensorType) },
                    onDisconnect = { onDisconnectSlot(sensorType) },
                    onClear = { onClearSlot(sensorType) }
                )
            }

            // Section 3: General Scanner Header & Target Slot Filter Chips
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ScanningControlHeader(
                    isScanning = uiState.isScanning,
                    hasPermission = uiState.permissionsState.hasBluetoothPermission,
                    onToggleScan = onToggleScan
                )
            }

            item {
                FilterAndTargetSlotRow(
                    targetSlot = uiState.targetSlot,
                    showAllDevices = uiState.showAllDevices,
                    onSetTargetSlot = onSetTargetSlot,
                    onSetShowAllDevices = onSetShowAllDevices
                )
            }

            // Section 4: All Discovered Devices
            item {
                val filterLabel = when (uiState.targetSlot) {
                    SensorType.POWER -> "Power Meters"
                    SensorType.HEART_RATE -> "Heart Rate Monitors"
                    SensorType.RADAR -> "Radar Devices"
                    null -> "All Sensors"
                }
                Text(
                    text = "Discovered Devices ($filterLabel - ${uiState.discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.discoveredDevices.isEmpty()) {
                item {
                    EmptyDiscoveredDevicesCard(isScanning = uiState.isScanning)
                }
            } else {
                items(
                    items = uiState.discoveredDevices,
                    key = { device -> device.address }
                ) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        targetSlot = uiState.targetSlot,
                        onAssignDevice = onAssignDevice
                    )
                }
            }
        }

        // Dedicated Pairing Sheet Modal per Sensor Slot
        if (uiState.activePairingSheetSlot != null) {
            PairingBottomSheet(
                targetSlot = uiState.activePairingSheetSlot,
                uiState = uiState,
                onDismiss = onClosePairingSheet,
                onToggleScan = onToggleScan,
                onSetShowAllDevices = onSetShowAllDevices,
                onPairDevice = { device ->
                    onAssignDevice(device, uiState.activePairingSheetSlot)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingBottomSheet(
    targetSlot: SensorType,
    uiState: ScannerUiState,
    onDismiss: () -> Unit,
    onToggleScan: () -> Unit,
    onSetShowAllDevices: (Boolean) -> Unit,
    onPairDevice: (DiscoveredDevice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val (title, icon) = when (targetSlot) {
        SensorType.POWER -> "Pair Power / Wattmeter" to Icons.Rounded.Speed
        SensorType.HEART_RATE -> "Pair Heart Rate Monitor" to Icons.Rounded.Favorite
        SensorType.RADAR -> "Pair Radar Device" to Icons.Rounded.Radar
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prioritizing compatible sensors nearby",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scan indicator
            if (uiState.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Controls Bar inside BottomSheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isScanning) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isScanning) "Stop Scan" else "Scan",
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Show All Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.showAllDevices,
                        onCheckedChange = onSetShowAllDevices,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Devices list
            if (uiState.discoveredDevices.isEmpty()) {
                EmptyDiscoveredDevicesCard(isScanning = uiState.isScanning)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(
                        items = uiState.discoveredDevices,
                        key = { device -> device.address }
                    ) { device ->
                        PairingDeviceItem(
                            device = device,
                            targetSlot = targetSlot,
                            onPair = { onPairDevice(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingDeviceItem(
    device: DiscoveredDevice,
    targetSlot: SensorType,
    onPair: () -> Unit
) {
    val isRecommended = device.isMatchFor(targetSlot)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Signal RSSI Column
            SignalStrengthColumn(rssi = device.rssi)

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isRecommended) {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Recommended Match",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else if (device.detectedType != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = device.detectedType.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onPair,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SignalStrengthColumn(rssi: Int) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    val icon: ImageVector
    val color: Color

    when {
        rssi >= -65 -> {
            icon = Icons.Rounded.SignalCellular4Bar
            color = Color(0xFF2E7D32)
        }
        rssi >= -80 -> {
            icon = Icons.Rounded.SignalCellular4Bar
            color = primaryColor
        }
        else -> {
            icon = Icons.Rounded.SignalCellular0Bar
            color = outlineColor
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Signal strength",
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$rssi dBm",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun FilterAndTargetSlotRow(
    targetSlot: SensorType?,
    showAllDevices: Boolean,
    onSetTargetSlot: (SensorType?) -> Unit,
    onSetShowAllDevices: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Slot Filter",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Show All",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = showAllDevices,
                    onCheckedChange = onSetShowAllDevices,
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = targetSlot == null,
                onClick = { onSetTargetSlot(null) },
                label = { Text("All", fontSize = 12.sp) }
            )
            FilterChip(
                selected = targetSlot == SensorType.POWER,
                onClick = { onSetTargetSlot(SensorType.POWER) },
                label = { Text("Power", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = targetSlot == SensorType.HEART_RATE,
                onClick = { onSetTargetSlot(SensorType.HEART_RATE) },
                label = { Text("Heart Rate", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = targetSlot == SensorType.RADAR,
                onClick = { onSetTargetSlot(SensorType.RADAR) },
                label = { Text("Radar", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
        }
    }
}

@Composable
private fun PermissionsSection(
    permissionsState: PermissionsState,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!permissionsState.hasBluetoothPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BluetoothDisabled,
                        contentDescription = "Bluetooth Required",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Required to scan and connect to cycling sensors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestBluetoothPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = "Grant", fontSize = 12.sp)
                    }
                }
            }
        }

        if (!permissionsState.hasOverlayPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = "Overlay Required",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Overlay Permission",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Required to show telemetry widgets over other apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onRequestOverlayPermission
                    ) {
                        Text(text = "Settings", fontSize = 12.sp)
                    }
                }
            }
        }

        if (permissionsState.hasBluetoothPermission && permissionsState.hasOverlayPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Permissions Granted",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bluetooth & System Overlay permissions granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorSlotCard(
    slot: SensorSlot,
    connectionState: ConnectionState,
    hasBluetoothPermission: Boolean,
    onScanAndPair: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit
) {
    val (title, icon) = when (slot.sensorType) {
        SensorType.POWER -> "Power & Cadence" to Icons.Rounded.Speed
        SensorType.HEART_RATE -> "Heart Rate" to Icons.Rounded.Favorite
        SensorType.RADAR -> "Garmin Varia / Radar" to Icons.Rounded.Radar
    }

    val isAssigned = !slot.macAddress.isNullOrBlank()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAssigned) slot.name.ifBlank { "Assigned Device" } else "No device paired",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAssigned && slot.macAddress != null) {
                        Text(
                            text = slot.macAddress,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                ConnectionStateBadge(connectionState = connectionState)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direct "Scan & Pair" button for each slot!
                FilledTonalButton(
                    onClick = onScanAndPair,
                    enabled = hasBluetoothPermission,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isAssigned) "Change / Pair" else "Scan & Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (isAssigned) {
                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Unpair",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING) {
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(text = "Disconnect", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onConnect,
                            enabled = hasBluetoothPermission,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(text = "Connect", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStateBadge(connectionState: ConnectionState) {
    val (bgColor, textColor, label) = when (connectionState) {
        ConnectionState.CONNECTED -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "Connected"
        )
        ConnectionState.CONNECTING -> Triple(
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
            "Connecting..."
        )
        ConnectionState.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Disconnected"
        )
        ConnectionState.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Error"
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ScanningControlHeader(
    isScanning: Boolean,
    hasPermission: Boolean,
    onToggleScan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isScanning) "Scanning for Sensors..." else "BLE Scanner Idle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isScanning) "Searching for nearby Bluetooth LE sensors" else "Start scan to search for devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = onToggleScan,
                    enabled = hasPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isScanning) "Stop Scan" else "Start Scan")
                }
            }

            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDiscoveredDevicesCard(isScanning: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isScanning) Icons.AutoMirrored.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isScanning) "Searching for Bluetooth LE sensors..." else "No devices discovered yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceItem(
    device: DiscoveredDevice,
    targetSlot: SensorType?,
    onAssignDevice: (DiscoveredDevice, SensorType) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val isRecommended = device.isMatchFor(targetSlot)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SignalStrengthColumn(rssi = device.rssi)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name ?: "Unknown Device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isRecommended && targetSlot != null) {
                            Surface(
                                color = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Recommended Match",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else if (device.detectedType != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Detected: ${device.detectedType.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Box {
                    Button(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = "Assign Slot", fontSize = 12.sp)
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "Assign Options",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Assign to Power / Cadence") },
                            leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                            onClick = {
                                dropdownExpanded = false
                                onAssignDevice(device, SensorType.POWER)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Assign to Heart Rate") },
                            leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                            onClick = {
                                dropdownExpanded = false
                                onAssignDevice(device, SensorType.HEART_RATE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Assign to Garmin Varia / Radar") },
                            leadingIcon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                            onClick = {
                                dropdownExpanded = false
                                onAssignDevice(device, SensorType.RADAR)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScannerScreenPreview() {
    CyclingSensorsOverlayTheme {
        ScannerScreenContent(
            uiState = ScannerUiState(
                isScanning = true,
                discoveredDevices = listOf(
                    DiscoveredDevice("Stages Power 9283", "AA:BB:CC:DD:EE:11", -58, SensorType.POWER),
                    DiscoveredDevice("Wahoo TICKR 4410", "AA:BB:CC:DD:EE:22", -72, SensorType.HEART_RATE),
                    DiscoveredDevice("Garmin Varia RTL515", "AA:BB:CC:DD:EE:33", -81, SensorType.RADAR)
                ),
                assignedSlots = mapOf(
                    SensorType.POWER to SensorSlot("power_slot", SensorType.POWER, "Stages Power 9283", "AA:BB:CC:DD:EE:11"),
                    SensorType.HEART_RATE to SensorSlot("hr_slot", SensorType.HEART_RATE, "", null),
                    SensorType.RADAR to SensorSlot("radar_slot", SensorType.RADAR, "", null)
                ),
                connectionStates = mapOf(
                    SensorType.POWER to ConnectionState.CONNECTED,
                    SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
                    SensorType.RADAR to ConnectionState.DISCONNECTED
                ),
                permissionsState = PermissionsState(
                    hasBluetoothPermission = true,
                    hasOverlayPermission = false
                )
            ),
            onRequestBluetoothPermissions = {},
            onRequestOverlayPermission = {},
            onToggleScan = {},
            onOpenPairingSheet = {},
            onClosePairingSheet = {},
            onSetTargetSlot = {},
            onSetShowAllDevices = {},
            onAssignDevice = { _, _ -> },
            onClearSlot = {},
            onConnectSlot = {},
            onDisconnectSlot = {}
        )
    }
}
