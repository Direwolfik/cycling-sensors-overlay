package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import cz.novotny.cyclingsensorsoverlay.domain.model.HeartRateData

/**
 * Binary parser for Bluetooth Low Energy Heart Rate Measurement notifications (GATT Characteristic 0x2A37 / Service 0x180D).
 *
 * Payload structure:
 * - Byte 0: Flags byte. Bit 0 specifies format (0 = uint8 heart rate value, 1 = uint16 heart rate value).
 * - Byte 1 (or Bytes 1..2): Heart rate measurement in BPM.
 */
class HeartRateParser {
    /**
     * Parses raw BLE notification bytes into a [HeartRateData] domain object.
     *
     * @param data Raw byte array from characteristic update notification.
     * @return [HeartRateData] containing current BPM, or null if payload is invalid/empty.
     */
    fun parse(data: ByteArray): HeartRateData? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0

        val bpm = if (is16Bit) {
            if (data.size < 3) return null
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            if (data.size < 2) return null
            data[1].toInt() and 0xFF
        }

        return HeartRateData(
            bpm = bpm.coerceAtLeast(0),
            timestamp = System.currentTimeMillis()
        )
    }
}
