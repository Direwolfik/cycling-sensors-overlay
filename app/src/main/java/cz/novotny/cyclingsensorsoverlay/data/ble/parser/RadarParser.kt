package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import android.util.Log
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat

/**
 * Robust binary parser for rear cycling radar threat notifications across standard (GATT 0x183C / 0x2B18)
 * and vendor custom protocols (e.g., Garmin Varia, Coospo TR70, Magene L508, Bryton Gardia, IGPSPORT).
 *
 * Payload structures handled:
 * 1) Standard GATT Cycling Radar (0x2B18):
 *    - Byte 0: Threat Count N (0 = clear, 1..N = targets).
 *    - Records (3 or 4 bytes per target):
 *      - Threat level: 0 = Clear, 1 = Approaching (Yellow), 2 = High Speed (Red).
 *      - Distance: 0..150m (clamped).
 *      - Speed: Relative speed (m/s converted to km/h).
 * 2) Garmin Varia / Vendor Raw Payloads:
 *    - Handled with or without header bytes, supporting 3-byte and 4-byte chunk streams.
 *    - Flexible threat level extraction (direct, upper/lower nibble bitfield).
 *    - Smart distance/threat byte ordering fallback.
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

        val hexString = data.joinToString(" ") { "%02X".format(it) }
        logDebug("Parsing raw BLE radar packet (len=${data.size}): [$hexString]")

        val threats = mutableListOf<RadarThreat>()

        try {
            // Case 1: Single byte payload (e.g. 0x00 = NO THREAT)
            if (data.size == 1) {
                val singleVal = data[0].toInt() and 0xFF
                logDebug("Single byte radar payload: $singleVal")
                return RadarData(
                    threats = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }

            val firstByte = data[0].toInt() and 0xFF
            val remainingLen = data.size - 1

            // Determine target count & record start offset
            val (targetCount, startOffset, recordSize) = when {
                // Header is magic Vendor prefix e.g. 0xFA, 0xAA
                firstByte >= 0xF0 -> {
                    val count = data[1].toInt() and 0xFF
                    val rSize = if ((data.size - 2) >= count * 4) 4 else 3
                    Triple(count, 2, rSize)
                }
                // Header is explicit count in 1..10 where remaining payload exactly matches target record size
                firstByte in 1..10 && (remainingLen == firstByte * 3 || remainingLen == firstByte * 4) -> {
                    val rSize = if (remainingLen == firstByte * 4) 4 else 3
                    Triple(firstByte, 1, rSize)
                }
                // No explicit count header: entire payload is target record chunks starting at index 0
                data.size % 4 == 0 -> Triple(data.size / 4, 0, 4)
                data.size % 3 == 0 -> Triple(data.size / 3, 0, 3)
                else -> {
                    // Fallback chunking: treat byte 0 as header if > 0, else 0
                    val count = if (firstByte in 1..10) firstByte else 10
                    Triple(count, if (firstByte in 1..10) 1 else 0, 3)
                }
            }

            if (targetCount == 0) {
                logDebug("Parsed 0 targets (Radar Clear)")
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            var offset = startOffset
            var targetId = 1

            while (offset + 1 < data.size && threats.size < targetCount) {
                val b0 = data[offset].toInt() and 0xFF
                val b1 = data[offset + 1].toInt() and 0xFF

                var b2 = 0
                var nextOffsetIncrement = 2

                if (recordSize >= 3 && offset + 2 < data.size) {
                    b2 = data[offset + 2].toInt() and 0xFF
                    nextOffsetIncrement = 3
                }
                if (recordSize >= 4 && offset + 3 < data.size) {
                    // If 4-byte chunk [ID, Threat, Dist, Speed]
                    val b3 = data[offset + 3].toInt() and 0xFF
                    nextOffsetIncrement = 4
                    val parsedThreat = decodeThreatLevel(b1)
                    val dist = b2.toFloat().coerceIn(0f, 150f)
                    val speed = (b3 * 3.6f).coerceIn(0f, 200f)

                    if (parsedThreat > 0 || dist > 0f) {
                        threats.add(
                            RadarThreat(
                                id = if (b0 > 0) b0 else targetId++,
                                threatLevel = parsedThreat,
                                distanceMeters = dist,
                                speedKmH = speed
                            )
                        )
                    }
                    offset += nextOffsetIncrement
                    continue
                }

                offset += nextOffsetIncrement

                // In 2 or 3 byte chunk: evaluate threat level vs distance with smart order fallback
                val (rawThreat, rawDist) = when {
                    b0 in 0..2 && b1 > 2 -> Pair(b0, b1)
                    b0 > 2 && b1 in 0..2 -> Pair(b1, b0)
                    else -> Pair(b0, b1)
                }

                val threatLevel = decodeThreatLevel(rawThreat)
                val distanceMeters = rawDist.toFloat().coerceIn(0f, 150f)
                val speedKmH = (b2 * 3.6f).coerceIn(0f, 200f)

                threats.add(
                    RadarThreat(
                        id = targetId++,
                        threatLevel = threatLevel,
                        distanceMeters = distanceMeters,
                        speedKmH = speedKmH
                    )
                )
            }
        } catch (t: Throwable) {
            logError("Error parsing radar notification payload", t)
        }

        val activeThreatCount = threats.count { it.threatLevel > 0 }
        logDebug("Parsed RadarData -> total targets: ${threats.size}, active threats (level > 0): $activeThreatCount, details: $threats")

        return RadarData(
            threats = threats,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun decodeThreatLevel(rawByte: Int): Int {
        if (rawByte == 0) return 0
        if (rawByte in 1..2) return rawByte

        // Check upper nibble (bits 4..6)
        val upperNibble = (rawByte shr 4) and 0x07
        if (upperNibble in 1..2) return upperNibble

        // Check lower nibble (bits 0..2)
        val lowerNibble = rawByte and 0x07
        if (lowerNibble in 1..2) return lowerNibble

        // Fallback for non-zero threat flags
        return 1
    }

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logError(msg: String, t: Throwable? = null) {
        try {
            Log.e(TAG, msg, t)
        } catch (_: Throwable) {
            println("[$TAG] $msg: ${t?.message}")
        }
    }

    companion object {
        private const val TAG = "RadarParser"
    }
}