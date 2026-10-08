package cz.novotny.cyclingsensorsoverlay.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.CyclingPowerParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.HeartRateParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.RadarParser
import cz.novotny.cyclingsensorsoverlay.domain.model.*
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-level Bluetooth Low Energy (BLE) manager handling multi-slot sensor connections, device discovery scanning,
 * GATT callbacks, automatic reconnection, universal service lookup fallback, and telemetry Flow emissions.
 * Fully instrumented with Firebase logging, custom session state keys, and non-fatal error recording for detailed
 * BLE and Radar stack debugging.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val powerParser = CyclingPowerParser()
    private val heartRateParser = HeartRateParser()
    private val radarParser = RadarParser()

    /** Hot stream emitting parsed cycling power and cadence telemetry data. */
    val powerDataStream = MutableStateFlow<PowerData?>(null)

    /** Hot stream emitting parsed heart rate telemetry data. */
    val heartRateDataStream = MutableStateFlow<HeartRateData?>(null)

    /** Hot stream emitting parsed rear radar vehicle threat telemetry data. */
    val radarDataStream = MutableStateFlow<RadarData?>(null)

    private val _isRadarSimulated = MutableStateFlow(false)

    /** State flow indicating whether simulated radar threat generation is active. */
    val isRadarSimulated: StateFlow<Boolean> = _isRadarSimulated.asStateFlow()

    private var simulationJob: Job? = null
    private var radarWatchdogTimeoutCount = 0

    /**
     * Enables or disables synthetic rear radar threat simulation.
     */
    fun setRadarSimulationEnabled(enabled: Boolean) {
        _isRadarSimulated.value = enabled
        AppLogger.i(TAG, "Radar simulation enabled set to: $enabled")
        AppLogger.setCustomKey("radar_simulation_enabled", enabled)

        simulationJob?.cancel()
        if (enabled) {
            simulationJob = scope.launch {
                var v1Dist = 145f
                var v2Dist = 95f
                val v1Speed = 72f
                val v2Speed = 42f

                while (isActive) {
                    delay(150L)

                    v1Dist -= (v1Speed / 3.6f) * 0.15f
                    if (v1Dist <= 0f) v1Dist = 150f

                    v2Dist -= (v2Speed / 3.6f) * 0.15f
                    if (v2Dist <= 0f) v2Dist = 150f

                    val threats = mutableListOf<RadarThreat>()
                    if (v1Dist in 0f..150f) {
                        threats.add(
                            RadarThreat(
                                id = 1,
                                threatLevel = ThreatLevel.HIGH_SPEED,
                                distanceMeters = v1Dist,
                                speedKmH = v1Speed
                            )
                        )
                    }
                    if (v2Dist in 0f..150f) {
                        threats.add(
                            RadarThreat(
                                id = 2,
                                threatLevel = ThreatLevel.APPROACHING,
                                distanceMeters = v2Dist,
                                speedKmH = v2Speed
                            )
                        )
                    }

                    radarDataStream.value = RadarData(
                        threats = threats.sortedBy { it.distanceMeters }
                    )
                }
            }
        } else {
            simulationJob = null
            radarDataStream.value = null
        }
    }

    private val _connectionStates = MutableStateFlow<Map<SensorType, ConnectionState>>(
        mapOf(
            SensorType.POWER to ConnectionState.DISCONNECTED,
            SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
            SensorType.RADAR to ConnectionState.DISCONNECTED
        )
    )

    /** State flow tracking connection lifecycle state for each sensor slot type. */
    val connectionStates: StateFlow<Map<SensorType, ConnectionState>> = _connectionStates.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    /** State flow emitting accumulated BLE devices discovered during active scanning. */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val activeGatts = ConcurrentHashMap<SensorType, BluetoothGatt>()
    private val activeSlots = ConcurrentHashMap<SensorType, SensorSlot>()
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredDevice>()

    private var leScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    /**
     * Starts low-latency BLE scan for nearby cycling sensors.
     */
    fun startScan(): Flow<List<DiscoveredDevice>> {
        if (isScanning) return discoveredDevices
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            AppLogger.w(TAG, "Bluetooth adapter is disabled or unavailable")
            AppLogger.setCustomKey("bluetooth_adapter_enabled", false)
            return discoveredDevices
        }

        AppLogger.setCustomKey("bluetooth_adapter_enabled", true)
        AppLogger.i(TAG, "Starting BLE active device scan")
        AppLogger.logEvent("ble_scan_started")

        leScanner = adapter.bluetoothLeScanner
        discoveredMap.clear()
        _discoveredDevices.value = emptyList()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                val name = result.scanRecord?.deviceName ?: device.name
                val rawRssi = result.rssi

                val existing = discoveredMap[address]
                val smoothedRssi = if (existing != null) {
                    val avg = existing.rssi * 0.8f + rawRssi * 0.2f
                    Math.round(avg / 5.0f) * 5
                } else {
                    Math.round(rawRssi / 5.0f) * 5
                }

                val rawServiceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

                val tempDiscovered = DiscoveredDevice(
                    name = name,
                    address = address,
                    rssi = smoothedRssi,
                    serviceUuids = rawServiceUuids
                )

                val discovered = tempDiscovered.copy(detectedType = tempDiscovered.inferredType)

                discoveredMap[address] = discovered
                _discoveredDevices.value = discoveredMap.values.toList()
            }

            override fun onScanFailed(errorCode: Int) {
                AppLogger.e(TAG, "BLE Scan failed with error code: $errorCode")
                AppLogger.logEvent("ble_scan_failed") {
                    putInt("error_code", errorCode)
                }
                isScanning = false
            }
        }

        scanCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            leScanner?.startScan(null, settings, callback)
            isScanning = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start BLE scan", e)
        }

        return discoveredDevices
    }

    /**
     * Stops active BLE device scan.
     */
    fun stopScan() {
        if (!isScanning) return
        AppLogger.i(TAG, "Stopping BLE active device scan")
        try {
            scanCallback?.let { leScanner?.stopScan(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error stopping scan", e)
        } finally {
            isScanning = false
            scanCallback = null
        }
    }

    /**
     * Initiates GATT connection to a sensor using its [slot] configuration.
     */
    fun connectSlot(slot: SensorSlot) {
        val mac = slot.macAddress
        if (mac.isNullOrBlank()) {
            AppLogger.w(TAG, "Cannot connect slot ${slot.slotId}: MAC address is empty")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            AppLogger.w(TAG, "Bluetooth unavailable or disabled when attempting to connect $mac")
            updateConnectionState(slot.sensorType, ConnectionState.ERROR)
            return
        }

        disconnectSlot(slot.sensorType)

        activeSlots[slot.sensorType] = slot
        updateConnectionState(slot.sensorType, ConnectionState.CONNECTING)

        AppLogger.i(TAG, "Connecting slot ${slot.sensorType} to MAC $mac")
        AppLogger.setCustomKey("${slot.sensorType.name.lowercase()}_mac", mac)
        AppLogger.logEvent("ble_connection_attempt") {
            putString("sensor_type", slot.sensorType.name)
            putString("mac", mac)
        }

        try {
            val device = adapter.getRemoteDevice(mac)
            val gattCallback = createGattCallback(slot.sensorType)
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt != null) {
                activeGatts[slot.sensorType] = gatt
            } else {
                AppLogger.e(TAG, "connectGatt returned null for $mac")
                updateConnectionState(slot.sensorType, ConnectionState.ERROR)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to device $mac", e)
            updateConnectionState(slot.sensorType, ConnectionState.ERROR)
        }
    }

    /**
     * Disconnects and closes active GATT connection for the specified [sensorType].
     */
    fun disconnectSlot(sensorType: SensorType) {
        AppLogger.i(TAG, "Disconnecting slot $sensorType")
        if (sensorType == SensorType.RADAR) {
            radarWatchdogJob?.cancel()
            radarWatchdogJob = null
            radarDataStream.value = null
        }
        val gatt = activeGatts.remove(sensorType)
        activeSlots.remove(sensorType)
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error closing GATT for $sensorType", e)
            }
        }
        updateConnectionState(sensorType, ConnectionState.DISCONNECTED)
    }

    private fun createGattCallback(sensorType: SensorType): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        AppLogger.i(TAG, "GATT Connected to $sensorType (status=$status). Discovering services...")
                        AppLogger.setCustomKey("${sensorType.name.lowercase()}_connection_state", "CONNECTED")
                        AppLogger.logEvent("ble_gatt_connected") {
                            putString("sensor_type", sensorType.name)
                            putInt("status", status)
                        }
                        updateConnectionState(sensorType, ConnectionState.CONNECTING)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        AppLogger.w(TAG, "GATT Disconnected from $sensorType (status=$status)")
                        AppLogger.setCustomKey("${sensorType.name.lowercase()}_connection_state", "DISCONNECTED")
                        AppLogger.logEvent("ble_gatt_disconnected") {
                            putString("sensor_type", sensorType.name)
                            putInt("status", status)
                        }
                        gatt.close()
                        activeGatts.remove(sensorType)
                        updateConnectionState(sensorType, ConnectionState.DISCONNECTED)

                        val slot = activeSlots[sensorType]
                        if (slot?.autoConnect == true) {
                            scope.launch {
                                delay(3000L)
                                if (activeSlots[sensorType]?.macAddress == slot.macAddress &&
                                    _connectionStates.value[sensorType] == ConnectionState.DISCONNECTED
                                ) {
                                    AppLogger.i(TAG, "Auto-reconnecting $sensorType to MAC ${slot.macAddress}")
                                    connectSlot(slot)
                                }
                            }
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    AppLogger.e(TAG, "Service discovery failed with status $status for $sensorType")
                    AppLogger.setCustomKey("${sensorType.name.lowercase()}_service_discovery_status", status)
                    updateConnectionState(sensorType, ConnectionState.ERROR)
                    return
                }

                val discoveredUuids = gatt.services.map { it.uuid.toString() }
                AppLogger.i(TAG, "Discovered ${gatt.services.size} services for $sensorType: $discoveredUuids")
                AppLogger.setCustomKey("${sensorType.name.lowercase()}_services", discoveredUuids.joinToString(","))

                for (service in gatt.services) {
                    AppLogger.d(TAG, "  Service UUID: ${service.uuid}")
                    for (char in service.characteristics) {
                        AppLogger.d(TAG, "    Char UUID: ${char.uuid}, props: 0x${char.properties.toString(16)}")
                    }
                }

                val characteristic = findCharacteristicForSensor(gatt, sensorType)
                if (characteristic == null) {
                    AppLogger.e(TAG, "Required service/characteristic not found for $sensorType in GATT services")
                    AppLogger.setCustomKey("${sensorType.name.lowercase()}_char_found", false)
                    updateConnectionState(sensorType, ConnectionState.ERROR)
                    return
                }

                AppLogger.i(TAG, "Selected primary characteristic ${characteristic.uuid} for $sensorType")
                AppLogger.setCustomKey("${sensorType.name.lowercase()}_primary_char", characteristic.uuid.toString())
                AppLogger.setCustomKey("${sensorType.name.lowercase()}_char_found", true)

                if (sensorType == SensorType.RADAR) {
                    for (service in gatt.services) {
                        for (c in service.characteristics) {
                            if (isNotifyOrIndicate(c)) {
                                val setOk = gatt.setCharacteristicNotification(c, true)
                                AppLogger.d(TAG, "Radar setCharacteristicNotification on ${c.uuid} -> $setOk")
                            }
                        }
                    }
                } else {
                    val enabled = gatt.setCharacteristicNotification(characteristic, true)
                    AppLogger.d(TAG, "setCharacteristicNotification on ${characteristic.uuid} -> $enabled")
                }

                val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
                if (descriptor != null) {
                    val props = characteristic.properties
                    val value = if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
                        (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                    ) {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }

                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, value)
                    } else {
                        @Suppress("DEPRECATION")
                        if (gatt.writeDescriptor(descriptor)) BluetoothGatt.GATT_SUCCESS else -1
                    }
                    val success = (res == BluetoothGatt.GATT_SUCCESS)
                    AppLogger.d(TAG, "writeDescriptor for CCCD on ${characteristic.uuid} returned: $success")
                    if (!success) {
                        AppLogger.w(TAG, "writeDescriptor for CCCD on ${characteristic.uuid} failed")
                    }
                } else {
                    AppLogger.w(TAG, "CCCD descriptor (0x2902) not found for characteristic ${characteristic.uuid}")
                }

                updateConnectionState(sensorType, ConnectionState.CONNECTED)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val success = (status == BluetoothGatt.GATT_SUCCESS)
                if (success) {
                    AppLogger.d(TAG, "onDescriptorWrite SUCCESS for char ${descriptor.characteristic?.uuid}")
                } else {
                    AppLogger.e(TAG, "onDescriptorWrite FAILED with status $status for char ${descriptor.characteristic?.uuid}")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                val hexString = value.joinToString(" ") { "%02X".format(it) }
                AppLogger.d(TAG, "onCharacteristicChanged (legacy) [$sensorType] [Char ${characteristic.uuid}]: $hexString")
                handleDataChanged(sensorType, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val hexString = value.joinToString(" ") { "%02X".format(it) }
                AppLogger.d(TAG, "onCharacteristicChanged [$sensorType] [Char ${characteristic.uuid}]: $hexString")
                handleDataChanged(sensorType, value)
            }
        }
    }

    private fun findCharacteristicForSensor(
        gatt: BluetoothGatt,
        sensorType: SensorType
    ): BluetoothGattCharacteristic? {
        val services = gatt.services ?: return null

        return when (sensorType) {
            SensorType.RADAR -> findRadarCharacteristic(services)
            SensorType.POWER -> findPowerCharacteristic(services)
            SensorType.HEART_RATE -> findHeartRateCharacteristic(services)
        }
    }

    private fun findRadarCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "183c") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2b18") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        val variaService = services.firstOrNull { uuidMatches(it.uuid, "6e400001") }
        if (variaService != null) {
            val char = variaService.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "6e400003") || uuidMatches(it.uuid, "6e400002") || uuidMatches(it.uuid, "2b18")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) return char
            val fallbackChar = variaService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "2b18") || uuidMatches(it.uuid, "6e400003") || uuidMatches(it.uuid, "6e400002")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) return char
        }

        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        return null
    }

    private fun findPowerCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "1818") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2a63") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull { uuidMatches(it.uuid, "2a63") && isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        return null
    }

    private fun findHeartRateCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "180d") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2a37") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull { uuidMatches(it.uuid, "2a37") && isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        for (service in services) {
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        return null
    }

    private fun isNotifyOrIndicate(char: BluetoothGattCharacteristic): Boolean {
        val props = char.properties
        return (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
               (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    }

    private fun isGenericService(serviceUuid: UUID): Boolean {
        val s = serviceUuid.toString().lowercase(Locale.ROOT)
        return s.startsWith("00001800") || s.startsWith("00001801")
    }

    private fun uuidMatches(uuid: UUID, shortHexOrFull: String): Boolean {
        val str = uuid.toString().lowercase(Locale.ROOT)
        val target = shortHexOrFull.lowercase(Locale.ROOT)
        return str == target || str.startsWith("0000$target") || str.startsWith(target)
    }

    private var radarWatchdogJob: Job? = null

    private fun resetRadarWatchdog() {
        radarWatchdogJob?.cancel()
        radarWatchdogJob = scope.launch {
            delay(3500L)
            if (radarDataStream.value?.threats?.isNotEmpty() == true) {
                radarWatchdogTimeoutCount++
                AppLogger.w(TAG, "Radar watchdog timeout (3.5s). Clearing stale radar threats.")
                AppLogger.setCustomKey("radar_watchdog_timeout_count", radarWatchdogTimeoutCount)
                AppLogger.logEvent("radar_watchdog_timeout") {
                    putInt("timeout_count", radarWatchdogTimeoutCount)
                }
                radarDataStream.value = RadarData(
                    threats = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun handleDataChanged(sensorType: SensorType, data: ByteArray) {
        when (sensorType) {
            SensorType.POWER -> {
                powerParser.parse(data)?.let { parsed ->
                    powerDataStream.value = parsed
                }
            }
            SensorType.HEART_RATE -> {
                heartRateParser.parse(data)?.let { parsed ->
                    heartRateDataStream.value = parsed
                }
            }
            SensorType.RADAR -> {
                resetRadarWatchdog()
                radarParser.parse(data)?.let { parsed ->
                    radarDataStream.value = parsed
                }
            }
        }
    }

    private fun updateConnectionState(sensorType: SensorType, state: ConnectionState) {
        val updated = _connectionStates.value.toMutableMap()
        updated[sensorType] = state
        _connectionStates.value = updated
        AppLogger.setCustomKey("${sensorType.name.lowercase()}_connection_state", state.name)
    }

    companion object {
        private const val TAG = "BleManager"
    }
}
