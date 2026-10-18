package cz.novotny.cyclingsensorsoverlay.data.ble.handler

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining sensor-specific BLE handling contract for characteristic resolution,
 * GATT setup, notification payload parsing, and lifecycle state management.
 */
interface SensorHandler<T> {
    val sensorType: SensorType
    val dataStream: StateFlow<T?>

    /**
     * Resolves the primary characteristic for this sensor from discovered GATT services.
     */
    fun findCharacteristic(services: List<BluetoothGattService>): BluetoothGattCharacteristic?

    /**
     * Configures GATT characteristic notifications, CCCD descriptor writes, or custom activation sequences.
     */
    fun setupGatt(
        gatt: BluetoothGatt,
        primaryChar: BluetoothGattCharacteristic,
        gattHelper: GattOperationHelper
    )

    /**
     * Processes incoming characteristic update notifications and updates [dataStream].
     */
    fun onDataReceived(characteristic: BluetoothGattCharacteristic, data: ByteArray)

    /**
     * Resets sensor telemetry state when connection is terminated.
     */
    fun onDisconnected()
}
