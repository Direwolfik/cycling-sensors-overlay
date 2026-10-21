package cz.novotny.cyclingsensorsoverlay.data.ble.handler

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

/**
 * Interface providing GATT write and notification configuration operations to sensor handlers.
 */
interface GattOperationHelper {
    fun setNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean = true
    ): Boolean

    fun queueCccdWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic)

    fun queueCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    )

    fun readCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean
}
