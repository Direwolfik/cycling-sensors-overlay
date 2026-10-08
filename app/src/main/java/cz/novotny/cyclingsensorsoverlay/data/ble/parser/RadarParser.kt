package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import android.util.Log
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel

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
            val firstByte = data[0].toInt() and 0xFF

            // Case 1: Header indicates 0 targets (e.g. 0x00, or payload starting with 0x00 = Radar Clear)
            if (firstByte == 0) {
                logDebug("Header indicates 0 targets (Radar Clear)")
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            val remainingLen = data.size - 1

            // Determine target count & record start offset & chunk size
            val (targetCount, startOffset, recordSize) = when {
                // Header is magic Vendor prefix e.g. 0xFA, 0xAA
                firstByte >= 0xF0 -> {
                    if (data.size < 2) return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
                    val count = data[1].toInt() and 0xFF
                    if (count == 0) return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
                    val rSize = if ((data.size - 2) >= count * 4) 4 else 3
                    Triple(count, 2, rSize)
                }
                // Header is explicit count in 1..10 where remaining payload matches target record size
                firstByte in 1..10 && (remainingLen == firstByte * 3 || remainingLen == firstByte * 4) -> {
                    val rSize = if (remainingLen == firstByte * 4) 4 else 3
                    Triple(firstByte, 1, rSize)
                }
                // No explicit count header: entire payload is target record chunks starting at index 0
                data.size % 4 == 0 -> Triple(data.size / 4, 0, 4)
                data.size % 3 == 0 -> Triple(data.size / 3, 0, 3)
                else -> {
                    val count = if (firstByte in 1..10) firstByte else (data.size / 3).coerceAtLeast(1)
                    val sOffset = if (firstByte in 1..10) 1 else 0
                    Triple(count, sOffset, 3)
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

                if (recordSize >= 4 && offset + 3 < data.size) {
                    // 4-byte chunk: [Target ID/Index, Threat Level, Distance, Speed]
                    val b2 = data[offset + 2].toInt() and 0xFF
                    val b3 = data[offset + 3].toInt() and 0xFF

                    val parsedThreat = decodeThreatLevel(b1)
                    val dist = b2.toFloat().coerceIn(0f, 150f)
                    val speed = (b3 * 3.6f).coerceIn(0f, 200f)

                    if (parsedThreat != ThreatLevel.NONE) {
                        threats.add(
                            RadarThreat(
                                id = if (b0 > 0) b0 else targetId++,
                                threatLevel = parsedThreat,
                                distanceMeters = dist,
                                speedKmH = speed
                            )
                        )
                    }
                    offset += 4
                    continue
                }

                if (offset + 2 < data.size) {
                    // 3-byte chunk: [b0, b1, b2]
                    val b2 = data[offset + 2].toInt() and 0xFF
                    offset += 3

                    val (threatLevel, distanceMeters, speedKmH, idVal) = when {
                        // b1 is threat level (0..2) and b2 is distance (> 2 or b0 in 1..10): [ID, Threat, Dist]
                        b1 in 0..2 && (b2 > 2 || b0 in 1..10) -> {
                            val t = decodeThreatLevel(b1)
                            val d = b2.toFloat().coerceIn(0f, 150f)
                            val id = if (b0 in 1..15) b0 else targetId++
                            Quad(t, d, 0f, id)
                        }
                        // b0 is threat level (0..2) and b1 is distance: [Threat, Dist, Speed]
                        b0 in 0..2 && b1 > 2 -> {
                            val t = decodeThreatLevel(b0)
                            val d = b1.toFloat().coerceIn(0f, 150f)
                            val s = (b2 * 3.6f).coerceIn(0f, 200f)
                            Quad(t, d, s, targetId++)
                        }
                        // b2 is threat level (0..2) and b1 is distance: [ID, Dist, Threat]
                        b2 in 0..2 && b1 > 2 -> {
                            val t = decodeThreatLevel(b2)
                            val d = b1.toFloat().coerceIn(0f, 150f)
                            val id = if (b0 in 1..15) b0 else targetId++
                            Quad(t, d, 0f, id)
                        }
                        // Fallback: search for threat level byte among b0, b1, b2
                        else -> {
                            val t = decodeThreatLevel(b0)
                            val d = maxOf(b1, b2).toFloat().coerceIn(0f, 150f)
                            Quad(t, d, 0f, targetId++)
                        }
                    }

                    if (threatLevel != ThreatLevel.NONE) {
                        threats.add(
                            RadarThreat(
                                id = idVal,
                                threatLevel = threatLevel,
                                distanceMeters = distanceMeters,
                                speedKmH = speedKmH
                            )
                        )
                    }
                } else {
                    // 2-byte chunk fallback
                    val threatLevel = decodeThreatLevel(b0)
                    val dist = b1.toFloat().coerceIn(0f, 150f)
                    if (threatLevel != ThreatLevel.NONE) {
                        threats.add(
                            RadarThreat(
                                id = targetId++,
                                threatLevel = threatLevel,
                                distanceMeters = dist,
                                speedKmH = 0f
                            )
                        )
                    }
                    offset += 2
                }
            }
        } catch (t: Throwable) {
            logError("Error parsing radar notification payload", t)
        }

        val activeThreats = threats.filter { it.threatLevel != ThreatLevel.NONE }
        logDebug("Parsed RadarData -> total targets: ${threats.size}, active threats (level > 0): ${activeThreats.size}, details: $activeThreats")

        return RadarData(
            threats = activeThreats,
            timestamp = System.currentTimeMillis()
        )
    }

    private data class Quad(
        val threatLevel: ThreatLevel,
        val distanceMeters: Float,
        val speedKmH: Float,
        val id: Int
    )

    private fun decodeThreatLevel(rawByte: Int): ThreatLevel {
        if (rawByte == 0) return ThreatLevel.NONE
        if (rawByte in 1..2) return ThreatLevel.fromValue(rawByte)

        // Check upper nibble (bits 4..6)
        val upperNibble = (rawByte shr 4) and 0x07
        if (upperNibble in 1..2) return ThreatLevel.fromValue(upperNibble)

        // Check lower nibble (bits 0..2)
        val lowerNibble = rawByte and 0x07
        if (lowerNibble in 1..2) return ThreatLevel.fromValue(lowerNibble)

        // Fallback for non-zero threat flags
        return ThreatLevel.APPROACHING
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