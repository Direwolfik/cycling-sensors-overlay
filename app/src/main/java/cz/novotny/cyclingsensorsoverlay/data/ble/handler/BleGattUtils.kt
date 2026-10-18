package cz.novotny.cyclingsensorsoverlay.data.ble.handler

import android.bluetooth.BluetoothGattCharacteristic
import java.util.Locale
import java.util.UUID

/**
 * Utility functions for GATT service and characteristic matching and property checks.
 */
object BleGattUtils {
    fun uuidMatches(uuid: UUID, shortHexOrFull: String): Boolean {
        val str = uuid.toString().lowercase(Locale.ROOT)
        val target = shortHexOrFull.lowercase(Locale.ROOT)
        return str == target || str.startsWith("0000$target") || str.startsWith(target)
    }

    fun isNotifyOrIndicate(char: BluetoothGattCharacteristic): Boolean {
        val props = char.properties
        return (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
               (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    }

    fun isWritable(char: BluetoothGattCharacteristic): Boolean {
        val props = char.properties
        return (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
               (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    }

    fun isGenericService(serviceUuid: UUID): Boolean {
        val s = serviceUuid.toString().lowercase(Locale.ROOT)
        return s.startsWith("00001800") || s.startsWith("00001801")
    }
}
