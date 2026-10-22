package cz.novotny.cyclingsensorsoverlay.data.ble.handler

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.isGenericService
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.isNotifyOrIndicate
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.isWritable
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.uuidMatches
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.RadarParser
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensor handler for Cycling Rear Radars (GATT Service 0x183C, Garmin Varia 0x6A4E3200, and vendor custom protocols).
 * Manages characteristic resolution, multi-service notify/write activation sequences,
 * raw payload parsing, synthetic radar threat simulation, and stale threat watchdog timeouts.
 */
class RadarSensorHandler(
    private val scope: CoroutineScope,
    private val parser: RadarParser = RadarParser()
) : SensorHandler<RadarData> {

    override val sensorType: SensorType = SensorType.RADAR

    private val _dataStream = MutableStateFlow<RadarData?>(null)
    override val dataStream: StateFlow<RadarData?> = _dataStream.asStateFlow()

    private val _isRadarSimulated = MutableStateFlow(false)
    val isRadarSimulated: StateFlow<Boolean> = _isRadarSimulated.asStateFlow()

    private var simulationJob: Job? = null
    private var radarWatchdogJob: Job? = null
    private var radarWatchdogTimeoutCount = 0
    private var hasLoggedFirstRadarPacket = false

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

                    _dataStream.value = RadarData(
                        threats = threats.sortedBy { it.distanceMeters }
                    )
                }
            }
        } else {
            simulationJob = null
            _dataStream.value = null
        }
    }

    override fun findCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
        // 1. Genuine Garmin Varia Radar Service (0x6A4E3200)
        val variaService = services.firstOrNull { uuidMatches(it.uuid, "6a4e3200") }
        if (variaService != null) {
            val char = variaService.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "6a4e3203") || uuidMatches(it.uuid, "6a4e3204")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) {
                AppLogger.i(TAG, "Found Garmin Varia radar characteristic ${char.uuid} in service ${variaService.uuid}")
                return char
            }
            val fallbackChar = variaService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // 2. Standard Cycling Radar Service (0x183C)
        val standardService = services.firstOrNull { uuidMatches(it.uuid, "183c") }
        if (standardService != null) {
            val char = standardService.characteristics.firstOrNull { uuidMatches(it.uuid, "2b18") && isNotifyOrIndicate(it) }
            if (char != null) return char
            val fallbackChar = standardService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // 3. Nordic UART Service (0x6E400001 - Coospo TR70, Magene L508, Bryton Gardia)
        val nusService = services.firstOrNull { uuidMatches(it.uuid, "6e400001") }
        if (nusService != null) {
            val char = nusService.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "6e400003") || uuidMatches(it.uuid, "6e400002") || uuidMatches(it.uuid, "2b18")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) return char
            val fallbackChar = nusService.characteristics.firstOrNull { isNotifyOrIndicate(it) }
            if (fallbackChar != null) return fallbackChar
        }

        // 4. Any characteristic matching known radar UUIDs across any discovered service
        for (service in services) {
            val char = service.characteristics.firstOrNull {
                (uuidMatches(it.uuid, "6a4e3203") || uuidMatches(it.uuid, "6a4e3204") ||
                        uuidMatches(it.uuid, "2b18") || uuidMatches(it.uuid, "6e400003") || uuidMatches(it.uuid, "6e400002")) &&
                        isNotifyOrIndicate(it)
            }
            if (char != null) return char
        }

        // 5. Fallback non-generic notify characteristic
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

    override fun setupGatt(
        gatt: BluetoothGatt,
        primaryChar: BluetoothGattCharacteristic,
        gattHelper: GattOperationHelper
    ) {
        val notifyChars = mutableListOf<String>()
        val writeChars = mutableListOf<String>()

        // 1. Subscribe to primary radar characteristic
        val setOk = gattHelper.setNotification(gatt, primaryChar, true)
        AppLogger.i(TAG, "Radar setNotification on primary char ${primaryChar.uuid} -> $setOk")
        notifyChars.add(primaryChar.uuid.toString())
        gattHelper.queueCccdWrite(gatt, primaryChar)

        // 2. Subscribe to all notify/indicate characteristics across all non-generic services
        for (service in gatt.services) {
            if (isGenericService(service.uuid)) continue
            for (c in service.characteristics) {
                if (c.uuid != primaryChar.uuid && isNotifyOrIndicate(c)) {
                    val subOk = gattHelper.setNotification(gatt, c, true)
                    AppLogger.d(TAG, "Radar secondary setNotification on ${c.uuid} -> $subOk")
                    notifyChars.add(c.uuid.toString())
                    gattHelper.queueCccdWrite(gatt, c)
                }
            }
        }

        // 3. Send radar activation commands to writable characteristics across non-generic services
        // Handles Garmin Varia Control (0x6A4E3201 / 0x6A4E3202), Nordic UART RX (0x6E400002), and vendor control points
        for (service in gatt.services) {
            if (isGenericService(service.uuid)) continue
            for (c in service.characteristics) {
                if (isWritable(c)) {
                    writeChars.add(c.uuid.toString())
                    AppLogger.i(TAG, "Sending radar activation [0x01, 0x01] and [0x00] to ${c.uuid}")
                    gattHelper.queueCharacteristicWrite(gatt, c, byteArrayOf(0x01, 0x01))
                    gattHelper.queueCharacteristicWrite(gatt, c, byteArrayOf(0x00))
                }
            }
        }

        AppLogger.logEvent("radar_characteristics_found") {
            putString("notify_chars", notifyChars.joinToString(","))
            putString("write_chars", writeChars.joinToString(","))
        }
        AppLogger.setCustomKey("radar_notify_chars", notifyChars.joinToString(","))
        AppLogger.setCustomKey("radar_write_chars", writeChars.joinToString(","))
    }

    override fun onDataReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (!hasLoggedFirstRadarPacket) {
            hasLoggedFirstRadarPacket = true
            val hexString = data.joinToString(" ") { "%02X".format(it) }
            AppLogger.i(TAG, "First Radar packet received! Hex: [$hexString]")
            AppLogger.logEvent("radar_first_packet_received") {
                putString("hex_payload", hexString)
                putInt("length", data.size)
            }
        }
        resetRadarWatchdog()
        parser.parse(data)?.let { parsed ->
            _dataStream.value = parsed
        }
    }

    private fun resetRadarWatchdog() {
        radarWatchdogJob?.cancel()
        radarWatchdogJob = scope.launch {
            delay(3500L)
            if (_dataStream.value?.threats?.isNotEmpty() == true) {
                radarWatchdogTimeoutCount++
                AppLogger.w(TAG, "Radar watchdog timeout (3.5s). Clearing stale radar threats.")
                AppLogger.setCustomKey("radar_watchdog_timeout_count", radarWatchdogTimeoutCount)
                AppLogger.logEvent("radar_watchdog_timeout") {
                    putInt("timeout_count", radarWatchdogTimeoutCount)
                }
                _dataStream.value = RadarData(
                    threats = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    override fun onDisconnected() {
        radarWatchdogJob?.cancel()
        simulationJob?.cancel()
        _dataStream.value = null
        hasLoggedFirstRadarPacket = false
        AppLogger.d(TAG, "RadarSensorHandler reset on disconnect")
    }

    companion object {
        private const val TAG = "RadarSensorHandler"
    }
}
