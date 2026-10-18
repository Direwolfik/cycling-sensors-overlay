package cz.novotny.cyclingsensorsoverlay.data.ble.handler

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.isGenericService
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.isNotifyOrIndicate
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.BleGattUtils.uuidMatches
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.CyclingPowerParser
import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensor handler for Cycling Power Meters (GATT Service 0x1818).
 * Manages characteristic resolution, CCCD notifications, and parsing power and cadence data.
 */
class PowerSensorHandler(
    private val parser: CyclingPowerParser = CyclingPowerParser()
) : SensorHandler<PowerData> {

    override val sensorType: SensorType = SensorType.POWER

    private val _dataStream = MutableStateFlow<PowerData?>(null)
    override val dataStream: StateFlow<PowerData?> = _dataStream.asStateFlow()

    override fun findCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic? {
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

    override fun setupGatt(
        gatt: BluetoothGatt,
        primaryChar: BluetoothGattCharacteristic,
        gattHelper: GattOperationHelper
    ) {
        gattHelper.setNotification(gatt, primaryChar, true)
        gattHelper.queueCccdWrite(gatt, primaryChar)
    }

    override fun onDataReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        parser.parse(data)?.let { parsed ->
            _dataStream.value = parsed
        }
    }

    override fun onDisconnected() {
        parser.reset()
        _dataStream.value = null
    }
}
