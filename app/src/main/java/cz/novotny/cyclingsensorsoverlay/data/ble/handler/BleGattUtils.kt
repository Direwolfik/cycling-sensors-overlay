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

    /**
     * Returns true if the service is a standard system/metadata service (Generic Access 0x1800,
     * Generic Attribute 0x1801, Device Information 0x180A, Battery 0x180F) or Nordic DFU service (0xFE59).
     */
    fun isSystemOrDfuService(serviceUuid: UUID): Boolean {
        val s = serviceUuid.toString().lowercase(Locale.ROOT)
        return s.startsWith("00001800") ||
               s.startsWith("00001801") ||
               s.startsWith("0000180a") ||
               s.startsWith("0000180f") ||
               s.startsWith("0000fe59") ||
               s.startsWith("8ec9")
    }
}
