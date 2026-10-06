package cz.novotny.cyclingsensorsoverlay.data.ble

import java.util.UUID

object BleConstants {
    // Cycling Power Service (0x1818) & Measurement Characteristic (0x2A63)
    val CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")

    // Heart Rate Service (0x180D) & Measurement Characteristic (0x2A37)
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    // Cycling Radar Service (Standard 0x183C & Characteristic 0x2B18)
    val RADAR_SERVICE_UUID: UUID = UUID.fromString("0000183c-0000-1000-8000-00805f9b34fb")
    val RADAR_DATA_CHAR_UUID: UUID = UUID.fromString("00002b18-0000-1000-8000-00805f9b34fb")

    // Garmin Varia / Coospo Custom Radar Service & Characteristics
    val VARIA_RADAR_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val VARIA_RADAR_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val VARIA_RADAR_TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    // Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
