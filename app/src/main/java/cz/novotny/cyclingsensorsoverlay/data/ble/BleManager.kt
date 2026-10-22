package cz.novotny.cyclingsensorsoverlay.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.*
import cz.novotny.cyclingsensorsoverlay.domain.model.*
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Queue

/**
 * Low-level Bluetooth Low Energy (BLE) manager handling multi-slot sensor connections, device discovery scanning,
 * GATT callbacks, write queues, automatic reconnection, and telemetry Flow emissions.
 *
 * Specific sensor parsing, setup sequences, and simulation logic are delegated to [SensorHandler] implementations.
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val powerHandler: PowerSensorHandler = PowerSensorHandler(),
    private val heartRateHandler: HeartRateSensorHandler = HeartRateSensorHandler(),
    private val radarHandler: RadarSensorHandler = RadarSensorHandler(scope)
) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val handlers: Map<SensorType, SensorHandler<*>> = mapOf(
        SensorType.POWER to powerHandler,
        SensorType.HEART_RATE to heartRateHandler,
        SensorType.RADAR to radarHandler
    )

    /** Hot stream emitting parsed cycling power and cadence telemetry data. */
    val powerDataStream: StateFlow<PowerData?> get() = powerHandler.dataStream

    /** Hot stream emitting parsed heart rate telemetry data. */
    val heartRateDataStream: StateFlow<HeartRateData?> get() = heartRateHandler.dataStream

    /** Hot stream emitting parsed rear radar vehicle threat telemetry data. */
    val radarDataStream: StateFlow<RadarData?> get() = radarHandler.dataStream

    /** State flow indicating whether simulated radar threat generation is active. */
    val isRadarSimulated: StateFlow<Boolean> get() = radarHandler.isRadarSimulated

    /** Enables or disables synthetic rear radar threat simulation. */
    fun setRadarSimulationEnabled(enabled: Boolean) {
        radarHandler.setRadarSimulationEnabled(enabled)
    }

    private val _connectionStates = MutableStateFlow<Map<SensorType, ConnectionState>>(
        mapOf(
            SensorType.POWER to ConnectionState.DISCONNECTED,
            SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
            SensorType.RADAR to ConnectionState.DISCONNECTED
        )
    )

    /** State flow tracking connection lifecycle state for each sensor slot type. */
    val connectionStates: StateFlow<Map<SensorType, ConnectionState>> =
        _connectionStates.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    /** State flow emitting accumulated BLE devices discovered during active scanning. */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val activeGatts = ConcurrentHashMap<SensorType, BluetoothGatt>()
    private val activeSlots = ConcurrentHashMap<SensorType, SensorSlot>()
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredDevice>()

    private var leScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    private val gattOperationHelper = object : GattOperationHelper {
        override fun setNotification(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            enable: Boolean
        ): Boolean {
            return gatt.setCharacteristicNotification(characteristic, enable)
        }

        override fun queueCccdWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            this@BleManager.queueCccdWrite(gatt, characteristic)
        }

        override fun queueCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            this@BleManager.queueCharacteristicWrite(gatt, characteristic, value)
        }

        override fun readCharacteristic(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ): Boolean {
            @Suppress("DEPRECATION")
            return gatt.readCharacteristic(characteristic)
        }
    }

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

                val rawServiceUuids =
                    result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

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
            val gatt =
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
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

    private sealed class GattWriteOp {
        data class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) :
            GattWriteOp()

        data class WriteCharacteristic(
            val characteristic: BluetoothGattCharacteristic,
            val value: ByteArray
        ) : GattWriteOp()
    }

    private val pendingGattWrites = ConcurrentHashMap<BluetoothGatt, Queue<GattWriteOp>>()

    private fun queueCccdWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID) ?: run {
            AppLogger.w(
                TAG,
                "CCCD descriptor (0x2902) not found for characteristic ${characteristic.uuid}"
            )
            return
        }
        val props = characteristic.properties
        val value = if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
            (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
        ) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        queueGattWrite(gatt, GattWriteOp.WriteDescriptor(descriptor, value))
    }

    private fun queueCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        queueGattWrite(gatt, GattWriteOp.WriteCharacteristic(characteristic, value))
    }

    private fun queueGattWrite(gatt: BluetoothGatt, op: GattWriteOp) {
        val queue = pendingGattWrites.getOrPut(gatt) { ConcurrentLinkedQueue() }
        queue.add(op)
        if (queue.size == 1) {
            processNextGattWrite(gatt)
        }
    }

    private fun processNextGattWrite(gatt: BluetoothGatt) {
        val queue = pendingGattWrites[gatt] ?: return
        val next = queue.peek() ?: run {
            pendingGattWrites.remove(gatt)
            return
        }

        when (next) {
            is GattWriteOp.WriteDescriptor -> {
                val descriptor = next.descriptor
                val value = next.value
                @Suppress("DEPRECATION")
                descriptor.value = value
                val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    if (gatt.writeDescriptor(descriptor)) BluetoothGatt.GATT_SUCCESS else -1
                }
                val success = (res == BluetoothGatt.GATT_SUCCESS)
                AppLogger.d(
                    TAG,
                    "Queued writeDescriptor for CCCD on ${descriptor.characteristic?.uuid} initiated -> $success"
                )
                AppLogger.logEvent("ble_descriptor_write_attempt") {
                    putString("char_uuid", descriptor.characteristic?.uuid.toString())
                    putBoolean("success", success)
                }
                if (!success) {
                    AppLogger.w(
                        TAG,
                        "writeDescriptor failed to initiate on ${descriptor.characteristic?.uuid}"
                    )
                    queue.poll()
                    processNextGattWrite(gatt)
                }
            }

            is GattWriteOp.WriteCharacteristic -> {
                val characteristic = next.characteristic
                val value = next.value
                val hex = value.joinToString(" ") { "%02X".format(it) }
                val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        value,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    if (gatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else -1
                }
                val success = (res == BluetoothGatt.GATT_SUCCESS)
                AppLogger.d(
                    TAG,
                    "Queued writeCharacteristic on ${characteristic.uuid} [$hex] initiated -> $success"
                )
                AppLogger.logEvent("radar_activation_sent") {
                    putString("char_uuid", characteristic.uuid.toString())
                    putString("hex_payload", hex)
                    putBoolean("success", success)
                }
                if (!success) {
                    AppLogger.w(
                        TAG,
                        "writeCharacteristic failed to initiate on ${characteristic.uuid}"
                    )
                    queue.poll()
                    processNextGattWrite(gatt)
                }
            }
        }
    }

    /**
     * Disconnects and closes active GATT connection for the specified [sensorType].
     */
    fun disconnectSlot(sensorType: SensorType) {
        AppLogger.i(TAG, "Disconnecting slot $sensorType")
        handlers[sensorType]?.onDisconnected()

        val gatt = activeGatts.remove(sensorType)
        activeSlots.remove(sensorType)
        if (gatt != null) {
            pendingGattWrites.remove(gatt)
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error closing GATT for $sensorType", e)
            }
        }
        updateConnectionState(sensorType, ConnectionState.DISCONNECTED)
    }

    private fun createGattCallback(sensorType: SensorType): BluetoothGattCallback =
        BluetoothGattCallbackImpl(sensorType)

    inner class BluetoothGattCallbackImpl(private val sensorType: SensorType) :
        BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    AppLogger.i(
                        TAG,
                        "GATT Connected to $sensorType (status=$status). Discovering services..."
                    )
                    AppLogger.setCustomKey(
                        "${sensorType.name.lowercase()}_connection_state",
                        "CONNECTED"
                    )
                    AppLogger.logEvent("ble_gatt_connected") {
                        putString("sensor_type", sensorType.name)
                        putInt("status", status)
                    }
                    updateConnectionState(sensorType, ConnectionState.CONNECTING)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    AppLogger.w(TAG, "GATT Disconnected from $sensorType (status=$status)")
                    AppLogger.setCustomKey(
                        "${sensorType.name.lowercase()}_connection_state",
                        "DISCONNECTED"
                    )
                    AppLogger.logEvent("ble_gatt_disconnected") {
                        putString("sensor_type", sensorType.name)
                        putInt("status", status)
                    }

                    gatt.close()
                    activeGatts.remove(sensorType)
                    handlers[sensorType]?.onDisconnected()
                    updateConnectionState(sensorType, ConnectionState.DISCONNECTED)

                    val slot = activeSlots[sensorType]
                    if (slot?.autoConnect == true) {
                        scope.launch {
                            delay(3000L)
                            if (activeSlots[sensorType]?.macAddress == slot.macAddress &&
                                _connectionStates.value[sensorType] == ConnectionState.DISCONNECTED
                            ) {
                                AppLogger.i(
                                    TAG,
                                    "Auto-reconnecting $sensorType to MAC ${slot.macAddress}"
                                )
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
                AppLogger.setCustomKey(
                    "${sensorType.name.lowercase()}_service_discovery_status",
                    status
                )
                updateConnectionState(sensorType, ConnectionState.ERROR)
                return
            }

            val discoveredUuids = gatt.services.map { it.uuid.toString() }
            AppLogger.i(
                TAG,
                "Discovered ${gatt.services.size} services for $sensorType: $discoveredUuids"
            )
            AppLogger.setCustomKey(
                "${sensorType.name.lowercase()}_services",
                discoveredUuids.joinToString(",")
            )
            AppLogger.logEvent("ble_services_discovered") {
                putString("sensor_type", sensorType.name)
                putString("services", discoveredUuids.joinToString(","))
            }

            val handler = handlers[sensorType]
            val characteristic = handler?.findCharacteristic(gatt.services)
            if (characteristic == null) {
                AppLogger.e(
                    TAG,
                    "Required service/characteristic not found for $sensorType in GATT services"
                )
                AppLogger.setCustomKey("${sensorType.name.lowercase()}_char_found", false)
                updateConnectionState(sensorType, ConnectionState.ERROR)
                return
            }

            AppLogger.i(
                TAG,
                "Selected primary characteristic ${characteristic.uuid} for $sensorType"
            )
            AppLogger.setCustomKey(
                "${sensorType.name.lowercase()}_primary_char",
                characteristic.uuid.toString()
            )
            AppLogger.setCustomKey("${sensorType.name.lowercase()}_char_found", true)

            handler.setupGatt(gatt, characteristic, gattOperationHelper)

            updateConnectionState(sensorType, ConnectionState.CONNECTED)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = (status == BluetoothGatt.GATT_SUCCESS)
            AppLogger.d(
                TAG,
                "onDescriptorWrite SUCCESS=$success (status=$status) for char ${descriptor.characteristic?.uuid}"
            )
            AppLogger.logEvent("radar_cccd_write_result") {
                putString("char_uuid", descriptor.characteristic?.uuid.toString())
                putInt("status", status)
                putBoolean("success", success)
            }
            val queue = pendingGattWrites[gatt]
            if (!queue.isNullOrEmpty()) {
                queue.poll()
                processNextGattWrite(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = (status == BluetoothGatt.GATT_SUCCESS)
            AppLogger.d(
                TAG,
                "onCharacteristicWrite SUCCESS=$success (status=$status) for char ${characteristic.uuid}"
            )
            AppLogger.logEvent("radar_char_write_result") {
                putString("char_uuid", characteristic.uuid.toString())
                putInt("status", status)
                putBoolean("success", success)
            }
            val queue = pendingGattWrites[gatt]
            if (!queue.isNullOrEmpty()) {
                queue.poll()
                processNextGattWrite(gatt)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (sensorType == SensorType.RADAR) {
                val hexString = value.joinToString(" ") { "%02X".format(it) }
                AppLogger.d(
                    TAG,
                    "onCharacteristicChanged (legacy) [RADAR] [Char ${characteristic.uuid}]: $hexString"
                )
            }
            handlers[sensorType]?.onDataReceived(characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (sensorType == SensorType.RADAR) {
                val hexString = value.joinToString(" ") { "%02X".format(it) }
                AppLogger.d(
                    TAG,
                    "onCharacteristicChanged [RADAR] [Char ${characteristic.uuid}]: $hexString"
                )
            }
            handlers[sensorType]?.onDataReceived(characteristic, value)
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
