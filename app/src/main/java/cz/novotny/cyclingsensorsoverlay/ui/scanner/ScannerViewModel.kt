package cz.novotny.cyclingsensorsoverlay.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.novotny.cyclingsensorsoverlay.domain.model.ConnectionState
import cz.novotny.cyclingsensorsoverlay.domain.model.DiscoveredDevice
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.usecase.AssignSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ClearSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ConnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.DisconnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.GetAssignedSlotsUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ScanSensorsUseCase
import cz.novotny.cyclingsensorsoverlay.util.PermissionsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the sensor scanner screen and pairing bottom sheets.
 *
 * @property isScanning True if a BLE scan is actively running.
 * @property rawDiscoveredDevices Raw list of discovered devices received from the BLE scanner.
 * @property discoveredDevices Processed, filtered, and sorted list of devices ready for UI display.
 * @property assignedSlots Map of currently configured sensor slots keyed by [SensorType].
 * @property connectionStates Real-time connection states for each sensor slot.
 * @property permissionsState Runtime Bluetooth and location permissions status.
 * @property targetSlot Currently active target pairing slot filter (POWER, HEART_RATE, or RADAR), if any.
 * @property showAllDevices True if weak/unnamed device filtering is disabled.
 * @property activePairingSheetSlot Slot type for which the pairing bottom sheet is currently open, or null.
 */
data class ScannerUiState(
    val isScanning: Boolean = false,
    val rawDiscoveredDevices: List<DiscoveredDevice> = emptyList(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val assignedSlots: Map<SensorType, SensorSlot> = mapOf(
        SensorType.POWER to SensorSlot("power_slot", SensorType.POWER),
        SensorType.HEART_RATE to SensorSlot("hr_slot", SensorType.HEART_RATE),
        SensorType.RADAR to SensorSlot("radar_slot", SensorType.RADAR)
    ),
    val connectionStates: Map<SensorType, ConnectionState> = mapOf(
        SensorType.POWER to ConnectionState.DISCONNECTED,
        SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
        SensorType.RADAR to ConnectionState.DISCONNECTED
    ),
    val permissionsState: PermissionsState = PermissionsState(),
    val targetSlot: SensorType? = null,
    val showAllDevices: Boolean = false,
    val activePairingSheetSlot: SensorType? = null
)

/**
 * ViewModel managing sensor discovery scanning, slot pairing bottom sheet state,
 * throttled device list re-sorting, MAC assignment persistence, and runtime permission validation.
 */
class ScannerViewModel(
    private val scanSensorsUseCase: ScanSensorsUseCase,
    private val connectSensorUseCase: ConnectSensorUseCase,
    private val disconnectSensorUseCase: DisconnectSensorUseCase,
    private val getAssignedSlotsUseCase: GetAssignedSlotsUseCase,
    private val assignSlotUseCase: AssignSlotUseCase,
    private val clearSlotUseCase: ClearSlotUseCase,
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())

    /** Reactive StateFlow emitting the current [ScannerUiState]. */
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var lastSortTimestamp = 0L

    init {
        observeAssignedSlotsAndConnectionStates()
    }

    private fun observeAssignedSlotsAndConnectionStates() {
        viewModelScope.launch {
            combine(
                getAssignedSlotsUseCase(),
                bleRepository.connectionStateStream
            ) { slots, connections ->
                Pair(slots, connections)
            }.collect { (slots, connections) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        assignedSlots = slots,
                        connectionStates = connections
                    )
                }
            }
        }
    }

    /**
     * Updates runtime permission status and triggers auto-connect for saved sensor slots if Bluetooth permission is granted.
     *
     * @param permissionsState Current permissions status.
     */
    fun updatePermissionsState(permissionsState: PermissionsState) {
        _uiState.update { it.copy(permissionsState = permissionsState) }
        if (permissionsState.hasBluetoothPermission) {
            autoConnectSavedSlots()
        }
    }

    /**
     * Checks saved sensor slot configurations and automatically initiates connection for disconnected slots
     * with valid MAC addresses and autoConnect enabled.
     */
    private fun autoConnectSavedSlots() {
        val currentState = _uiState.value
        if (!currentState.permissionsState.hasBluetoothPermission) return

        currentState.assignedSlots.forEach { (type, slot) ->
            val isDisconnected = currentState.connectionStates[type] == ConnectionState.DISCONNECTED
            if (!slot.macAddress.isNullOrBlank() && slot.autoConnect && isDisconnected) {
                connectSensorUseCase(slot)
            }
        }
    }

    /**
     * Sets the active target pairing slot filter, re-sorting discovered devices so matching devices appear first.
     *
     * @param slot Target [SensorType] to pair, or null to clear target filtering.
     */
    fun setTargetSlot(slot: SensorType?) {
        _uiState.update { state ->
            state.copy(
                targetSlot = slot,
                discoveredDevices = processDevices(state.rawDiscoveredDevices, slot, state.showAllDevices, forceResort = true)
            )
        }
    }

    /**
     * Enables or disables showing all discovered devices (including weak RSSI and unnamed devices).
     *
     * @param showAll True to disable device filtering.
     */
    fun setShowAllDevices(showAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                showAllDevices = showAll,
                discoveredDevices = processDevices(state.rawDiscoveredDevices, state.targetSlot, showAll, forceResort = true)
            )
        }
    }

    /**
     * Opens the pairing bottom sheet for a specific [slot] type, setting the target slot filter
     * and starting a BLE scan if permissions are granted.
     *
     * @param slot [SensorType] to pair.
     */
    fun openPairingSheet(slot: SensorType) {
        _uiState.update { state ->
            state.copy(
                activePairingSheetSlot = slot,
                targetSlot = slot,
                discoveredDevices = processDevices(state.rawDiscoveredDevices, slot, state.showAllDevices, forceResort = true)
            )
        }
        if (!_uiState.value.isScanning && _uiState.value.permissionsState.hasBluetoothPermission) {
            startScan()
        }
    }

    /** Closes the active pairing bottom sheet. */
    fun closePairingSheet() {
        _uiState.update { it.copy(activePairingSheetSlot = null) }
    }

    /** Toggles the BLE scanning state on or off. */
    fun toggleScan() {
        if (_uiState.value.isScanning) {
            stopScan()
        } else {
            startScan()
        }
    }

    /**
     * Starts BLE device discovery scan if Bluetooth permissions are granted.
     * Resets device lists and subscribes to raw scan emissions.
     */
    fun startScan() {
        if (!_uiState.value.permissionsState.hasBluetoothPermission) {
            return
        }

        scanJob?.cancel()
        lastSortTimestamp = 0L
        _uiState.update { state ->
            state.copy(
                isScanning = true,
                rawDiscoveredDevices = emptyList(),
                discoveredDevices = emptyList()
            )
        }

        scanJob = viewModelScope.launch {
            scanSensorsUseCase.startScan().collect { devices ->
                _uiState.update { state ->
                    state.copy(
                        rawDiscoveredDevices = devices,
                        discoveredDevices = processDevices(devices, state.targetSlot, state.showAllDevices)
                    )
                }
            }
        }
    }

    /** Stops active BLE scan. */
    fun stopScan() {
        scanSensorsUseCase.stopScan()
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    /**
     * Assigns a discovered BLE device to a specified sensor slot, persisting configuration to DataStore
     * and attempting immediate connection if permissions permit.
     *
     * @param device Discovered BLE device to assign.
     * @param sensorType Target slot classification.
     */
    fun assignDeviceToSlot(device: DiscoveredDevice, sensorType: SensorType) {
        val newSlot = SensorSlot(
            slotId = "${sensorType.name.lowercase()}_slot",
            sensorType = sensorType,
            name = device.name ?: "Unknown Device",
            macAddress = device.address,
            autoConnect = true
        )

        viewModelScope.launch {
            assignSlotUseCase(newSlot, autoConnect = _uiState.value.permissionsState.hasBluetoothPermission)
        }
        closePairingSheet()
    }

    /**
     * Disconnects and removes saved device assignment for the given [sensorType].
     */
    fun clearSlotAssignment(sensorType: SensorType) {
        viewModelScope.launch {
            clearSlotUseCase(sensorType)
        }
    }

    /**
     * Connects to the saved MAC address assigned to [sensorType].
     */
    fun connectSlot(sensorType: SensorType) {
        val slot = _uiState.value.assignedSlots[sensorType] ?: return
        if (!slot.macAddress.isNullOrBlank() && _uiState.value.permissionsState.hasBluetoothPermission) {
            connectSensorUseCase(slot)
        }
    }

    /**
     * Disconnects active BLE GATT session for [sensorType].
     */
    fun disconnectSlot(sensorType: SensorType) {
        disconnectSensorUseCase(sensorType)
    }

    /**
     * Filters raw device list and applies throttled sorting.
     *
     * To avoid aggressive UI jumping during active scanning, list re-sorting is throttled
     * to a 1,500 ms window ([SORT_THROTTLE_INTERVAL_MS]). Between throttle ticks, existing item ordering
     * is preserved while updated RSSI values and newly discovered devices are appended.
     */
    private fun processDevices(
        rawList: List<DiscoveredDevice>,
        targetSlot: SensorType?,
        showAllDevices: Boolean,
        forceResort: Boolean = false
    ): List<DiscoveredDevice> {
        val filtered = if (showAllDevices) {
            rawList
        } else {
            rawList.filterNot { device ->
                device.name.isNullOrBlank() && device.serviceUuids.isEmpty() && device.rssi < -90
            }
        }

        val currentTime = System.currentTimeMillis()
        val currentList = _uiState.value.discoveredDevices

        if (forceResort || currentList.isEmpty() || (currentTime - lastSortTimestamp) >= SORT_THROTTLE_INTERVAL_MS) {
            lastSortTimestamp = currentTime
            return sortDevices(filtered, targetSlot)
        } else {
            val rawMap = filtered.associateBy { it.address }
            val updatedCurrent = currentList.mapNotNull { oldDev ->
                rawMap[oldDev.address]?.copy(detectedType = oldDev.detectedType ?: rawMap[oldDev.address]?.inferredType)
            }
            val currentAddresses = currentList.map { it.address }.toSet()
            val newDevices = filtered.filterNot { it.address in currentAddresses }
            val sortedNewDevices = sortDevices(newDevices, targetSlot)

            return updatedCurrent + sortedNewDevices
        }
    }

    /**
     * Sorts devices prioritized by target slot match first, descending RSSI second, and device name/address third.
     */
    private fun sortDevices(
        devices: List<DiscoveredDevice>,
        targetSlot: SensorType?
    ): List<DiscoveredDevice> {
        return devices.sortedWith(
            compareByDescending<DiscoveredDevice> { device ->
                device.isMatchFor(targetSlot)
            }.thenByDescending { device ->
                device.rssi
            }.thenBy { device ->
                device.name ?: device.address
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    companion object {
        private const val SORT_THROTTLE_INTERVAL_MS = 1500L
    }
}
