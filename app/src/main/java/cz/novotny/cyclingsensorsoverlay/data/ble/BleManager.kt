package cz.novotny.cyclingsensorsoverlay.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.CyclingPowerParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.HeartRateParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.RadarParser
import cz.novotny.cyclingsensorsoverlay.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-level Bluetooth Low Energy (BLE) manager handling multi-slot sensor connections, device discovery scanning,
 * GATT callbacks, automatic reconnection, universal service lookup fallback, and telemetry Flow emissions.
 *
 * Key Capabilities:
 * - Multi-Slot BLE Management: Simultaneously manages independent GATT connections for Power Meter, Heart Rate Monitor, and Rear Radar.
 * - Low-Latency Scanning: Performs active BLE discovery scans, applying exponential moving average smoothing and step quantization (5 dBm) to RSSI readings.
 * - Universal Service Lookup Fallback: Locates notification characteristics using standard GATT UUIDs (0x1818, 0x180D, 0x183C), vendor custom UUIDs (e.g., Varia 0x6E40), or deep scan fallback across all discovered services.
 * - Auto-Reconnect: Schedules automated reconnection attempts 3 seconds after an unexpected GATT disconnection for auto-connect enabled slots.
 * - Radar Simulation Mode: Generates synthetic dual-vehicle threat trajectories for testing overlays without physical radar hardware.
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

    /**
     * Enables or disables synthetic rear radar threat simulation.
     *
     * When enabled, launches a coroutine generating two simulated approaching vehicles
     * (a high-speed red threat and a moderate-speed yellow threat) cycling every 150ms.
     *
     * @param enabled True to start simulation, false to stop and reset radar telemetry.
     */
    fun setRadarSimulationEnabled(enabled: Boolean) {
        _isRadarSimulated.value = enabled
        simulationJob?.cancel()
        if (enabled) {
            simulationJob = scope.launch {
                var v1Dist = 145f
                var v2Dist = 95f
                val v1Speed = 72f // km/h (High speed red threat)
                val v2Speed = 42f // km/h (Approaching yellow threat)

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
                                threatLevel = 2,
                                distanceMeters = v1Dist,
                                speedKmH = v1Speed
                            )
                        )
                    }
                    if (v2Dist in 0f..150f) {
                        threats.add(
                            RadarThreat(
                                id = 2,
                                threatLevel = 1,
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
     *
     * Applies exponential moving average RSSI smoothing (80% historical, 20% raw sample)
     * and quantizes RSSI values to 5 dBm intervals to reduce list flickering in UI.
     *
     * @return Flow emitting list of discovered devices updated in real time.
     */
    fun startScan(): Flow<List<DiscoveredDevice>> {
        if (isScanning) return discoveredDevices
        val adapter = bluetoothAdapter ?: return discoveredDevices

        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter is disabled")
            return discoveredDevices
        }

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
                Log.e(TAG, "Scan failed with error code: $errorCode")
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
            Log.e(TAG, "Failed to start BLE scan", e)
        }

        return discoveredDevices
    }

    /**
     * Stops active BLE device scan and releases scan callback resources.
     */
    fun stopScan() {
        if (!isScanning) return
        try {
            scanCallback?.let { leScanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            isScanning = false
            scanCallback = null
        }
    }

    /**
     * Initiates GATT connection to a sensor using its [slot] configuration.
     *
     * Clears any existing GATT session for the slot's [SensorType] prior to connecting.
     *
     * @param slot Target slot metadata containing device MAC address and sensor type.
     */
    fun connectSlot(slot: SensorSlot) {
        val mac = slot.macAddress
        if (mac.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect slot ${slot.slotId}: MAC address is empty")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth unavailable or disabled")
            updateConnectionState(slot.sensorType, ConnectionState.ERROR)
            return
        }

        disconnectSlot(slot.sensorType)

        activeSlots[slot.sensorType] = slot
        updateConnectionState(slot.sensorType, ConnectionState.CONNECTING)

        try {
            val device = adapter.getRemoteDevice(mac)
            val gattCallback = createGattCallback(slot.sensorType)
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt != null) {
                activeGatts[slot.sensorType] = gatt
            } else {
                updateConnectionState(slot.sensorType, ConnectionState.ERROR)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device $mac", e)
            updateConnectionState(slot.sensorType, ConnectionState.ERROR)
        }
    }

    /**
     * Disconnects and closes active GATT connection for the specified [sensorType].
     *
     * @param sensorType Sensor type slot to disconnect.
     */
    fun disconnectSlot(sensorType: SensorType) {
        val gatt = activeGatts.remove(sensorType)
        activeSlots.remove(sensorType)
        if (gatt != null) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT for $sensorType", e)
            }
        }
        updateConnectionState(sensorType, ConnectionState.DISCONNECTED)
    }

    /**
     * Creates a [BluetoothGattCallback] instance for monitoring connection state changes,
     * discovering services, enabling CCCD notification descriptors, receiving notification payloads,
     * and scheduling auto-reconnects on unexpected disconnections.
     */
    private fun createGattCallback(sensorType: SensorType): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to $sensorType. Discovering services...")
                        updateConnectionState(sensorType, ConnectionState.CONNECTING)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $sensorType")
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
                                    Log.d(TAG, "Auto-reconnecting $sensorType")
                                    connectSlot(slot)
                                }
                            }
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed with status $status for $sensorType")
                    updateConnectionState(sensorType, ConnectionState.ERROR)
                    return
                }

                Log.d(TAG, "Discovered services for $sensorType:")
                for (service in gatt.services) {
                    Log.d(TAG, "  Service UUID: ${service.uuid}")
                    for (char in service.characteristics) {
                        Log.d(TAG, "    Char UUID: ${char.uuid}, props: 0x${char.properties.toString(16)}")
                    }
                }

                val characteristic = findCharacteristicForSensor(gatt, sensorType)
                if (characteristic == null) {
                    Log.e(TAG, "Required service/characteristic not found for $sensorType")
                    updateConnectionState(sensorType, ConnectionState.ERROR)
                    return
                }

                Log.d(TAG, "Selected primary characteristic ${characteristic.uuid} for $sensorType")

                if (sensorType == SensorType.RADAR) {
                    // For Radar, enable notifications/indications on ALL notify/indicate characteristics found across services
                    for (service in gatt.services) {
                        for (c in service.characteristics) {
                            if (isNotifyOrIndicate(c)) {
                                val setOk = gatt.setCharacteristicNotification(c, true)
                                Log.d(TAG, "Radar setCharacteristicNotification on ${c.uuid} -> $setOk")
                            }
                        }
                    }
                } else {
                    val enabled = gatt.setCharacteristicNotification(characteristic, true)
                    Log.d(TAG, "setCharacteristicNotification on ${characteristic.uuid} -> $enabled")
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

                    val res = gatt.writeDescriptor(descriptor, value)
                    val success = (res == BluetoothGatt.GATT_SUCCESS)
                    Log.d(TAG, "writeDescriptor for CCCD on ${characteristic.uuid} returned: $success")
                } else {
                    Log.w(TAG, "CCCD descriptor (0x2902) not found for characteristic ${characteristic.uuid}")
                }

                updateConnectionState(sensorType, ConnectionState.CONNECTED)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Log.d(TAG, "onDescriptorWrite for char ${descriptor.characteristic?.uuid}, status=$status (${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED"})")
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                Log.d(TAG, "onCharacteristicChanged (legacy) [$sensorType] [Char ${characteristic.uuid}]: ${value.joinToString(" ") { "%02X".format(it) }}")
                handleDataChanged(sensorType, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                Log.d(TAG, "onCharacteristicChanged [$sensorType] [Char ${characteristic.uuid}]: ${value.joinToString(" ") { "%02X".format(it) }}")
                handleDataChanged(sensorType, value)
            }
        }
    }

    /**
     * Resolves the target notification characteristic for a given [sensorType] using multi-tier lookup strategies.
     */
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

    /**
     * Universal fallback service lookup for Radar sensors.
     * Evaluates standard Cycling Radar (0x183C / 0x2B18), vendor custom (Varia/Coospo 0x6E40),
     * and deep scans all non-generic services for notify/indicate characteristics.
     */
    private fun findRadarCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        // a) Cycling Radar Service 0000183c-0000-1000-8000-00805f9b34fb or short 0x183C
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "183c") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2b18") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // b) Garmin Varia / Coospo custom radar service 6e400001-b5a3-f393-e0a9-e50e24dcca9e
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

        // c) Fallback: Search ALL discovered services on the radar device
        // Pass 1: characteristic with UUID 00002b18..., 6e400003..., or 6e400002...
        for (service in services) {
            val char = service.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "2b18") || uuidMatches(it.uuid, "6e400003") || uuidMatches(it.uuid, "6e400002")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) return char
        }

        // Pass 2: ANY characteristic in non-generic services with PROPERTY_NOTIFY or PROPERTY_INDICATE
        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        // Pass 3: ANY characteristic in ANY service with PROPERTY_NOTIFY or PROPERTY_INDICATE
        for (service in services) {
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        return null
    }

    /**
     * Universal fallback service lookup for Power Meters.
     * Checks standard Cycling Power Service (0x1818 / 0x2A63), followed by cross-service 0x2A63 search,
     * and general non-generic service notify/indicate characteristic search.
     */
    private fun findPowerCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        // a) Cycling Power Service 00001818-0000-1000-8000-00805f9b34fb or short 0x1818
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "1818") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2a63") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // Fallback Pass 1: Search ALL services for 2a63 characteristic
        for (service in services) {
            val char = service.characteristics.firstOrNull { uuidMatches(it.uuid, "2a63") && isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        // Fallback Pass 2: ANY characteristic in non-generic services with PROPERTY_NOTIFY or PROPERTY_INDICATE
        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        // Fallback Pass 3: ANY characteristic in ANY service with PROPERTY_NOTIFY or PROPERTY_INDICATE
        for (service in services) {
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        return null
    }

    /**
     * Universal fallback service lookup for Heart Rate Monitors.
     * Checks standard Heart Rate Service (0x180D / 0x2A37), followed by cross-service 0x2A37 search,
     * and general non-generic service notify/indicate characteristic search.
     */
    private fun findHeartRateCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        // a) Heart Rate Service 0000180d-0000-1000-8000-00805f9b34fb or short 0x180D
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "180d") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2a37") }
            if (char != null && isNotifyOrIndicate(char)) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // Fallback Pass 1: Search ALL services for 2a37 characteristic
        for (service in services) {
            val char = service.characteristics.firstOrNull { uuidMatches(it.uuid, "2a37") && isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        // Fallback Pass 2: ANY characteristic in non-generic services with PROPERTY_NOTIFY or PROPERTY_INDICATE
        for (service in services) {
            if (isGenericService(service.uuid)) continue
            val char = service.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (char != null) return char
        }

        // Fallback Pass 3: ANY characteristic in ANY service with PROPERTY_NOTIFY or PROPERTY_INDICATE
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
    }

    companion object {
        private const val TAG = "BleManager"
    }
}
