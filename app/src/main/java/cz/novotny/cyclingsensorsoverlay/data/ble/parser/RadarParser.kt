package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat

/**
 * Binary parser for rear cycling radar threat notifications across standard (GATT 0x183C / 0x2B18)
 * and vendor-custom protocols (e.g., Garmin Varia / Coospo 0x6E40).
 *
 * Payload structure:
 * - Byte 0: Header / Target count indicator (0 = no targets detected; 1..10 = explicit threat count).
 * - Target records (2 or 3 bytes per vehicle target):
 *   - Byte 0: Threat level urgency (0 = clear/low, 1..2 = medium/high or bitmask flags).
 *   - Byte 1: Raw distance in meters (coerced to 0..250m).
 *   - Byte 2 (optional): Relative approach speed in m/s, converted to km/h (* 3.6f, coerced to 0..200 km/h).
 */
class RadarParser {
    /**
     * Parses raw BLE notification bytes into a [RadarData] snapshot containing vehicle threat list.
     *
     * @param data Raw notification payload, or null.
     * @return [RadarData] containing detected [RadarThreat] targets, or empty list if no threats detected/null payload.
     */
    fun parse(data: ByteArray?): RadarData? {
        if (data == null || data.isEmpty()) return null

        val threats = mutableListOf<RadarThreat>()

        try {
            if (data.size >= 3) {
                val header = data[0].toInt() and 0xFF

                if (header == 0) {
                    // 0 targets detected by radar
                    return RadarData(
                        threats = emptyList(),
                        timestamp = System.currentTimeMillis()
                    )
                }

                val hasExplicitHeader = header in 1..10
                val targetCount = if (hasExplicitHeader) header else 10
                var offset = if (hasExplicitHeader) 1 else 0
                var targetId = 1

                while (offset + 1 < data.size && threats.size < targetCount) {
                    val rawThreat = data[offset].toInt() and 0xFF
                    val rawDist = data[offset + 1].toInt() and 0xFF

                    var rawSpeed = 0
                    if (offset + 2 < data.size) {
                        rawSpeed = data[offset + 2].toInt() and 0xFF
                        offset += 3
                    } else {
                        offset += 2
                    }

                    val threatLevel = when {
                        rawThreat == 0 -> 0
                        rawThreat in 1..2 -> rawThreat
                        else -> (rawThreat and 0x03).coerceIn(0, 2)
                    }

                    val distanceMeters = rawDist.toFloat().coerceIn(0f, 250f)
                    val speedKmH = (rawSpeed * 3.6f).coerceIn(0f, 200f)

                    threats.add(
                        RadarThreat(
                            id = targetId++,
                            threatLevel = threatLevel,
                            distanceMeters = distanceMeters,
                            speedKmH = speedKmH
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Gracefully ignore unexpected malformed payload
        }

        return RadarData(
            threats = threats,
            timestamp = System.currentTimeMillis()
        )
    }
}
