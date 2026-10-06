package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Supported types of cycling sensors managed by the overlay system.
 */
enum class SensorType {
    /** Cycling Power Meter (GATT Service 0x1818). */
    POWER,

    /** Heart Rate Monitor (GATT Service 0x180D). */
    HEART_RATE,

    /** Rear Cycling Radar (GATT Service 0x183C or Varia custom protocol). */
    RADAR
}
