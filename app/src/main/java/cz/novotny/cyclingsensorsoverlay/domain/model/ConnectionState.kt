package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Enumerates the lifecycle connection states for a Bluetooth Low Energy (BLE) sensor slot.
 */
enum class ConnectionState {
    /** Sensor is disconnected. */
    DISCONNECTED,

    /** Connection attempt or GATT service discovery is currently in progress. */
    CONNECTING,

    /** Active GATT connection established and notifications enabled. */
    CONNECTED,

    /** Connection or GATT service discovery encountered an error. */
    ERROR
}
