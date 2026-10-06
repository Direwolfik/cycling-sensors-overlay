package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Represents a saved configuration slot for a specific sensor type.
 *
 * @property slotId Unique key identifying the slot (e.g., "power_slot").
 * @property sensorType Sensor classification assigned to this slot.
 * @property name User-visible display name or device name.
 * @property macAddress Hardware MAC address of the paired BLE device, or null if unassigned.
 * @property autoConnect Whether the application should automatically connect to this slot on launch or scan.
 */
data class SensorSlot(
    val slotId: String,
    val sensorType: SensorType,
    val name: String = "",
    val macAddress: String? = null,
    val autoConnect: Boolean = true
)
