package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import cz.novotny.cyclingsensorsoverlay.util.AppLogger

/**
 * Robust binary parser for rear cycling radar threat notifications across standard (GATT 0x183C / 0x2B18)
 * and vendor custom protocols (e.g., Garmin Varia 0x6A4E3200, Coospo TR70, Magene L508, Bryton Gardia, IGPSPORT).
 *
 * Payload structures handled:
 * 0) Coospo TR70 Framed BLE Stream (0xFDB1, Header 0xC8):
 *    - Frame: [0xC8, length, commandId, ...payload..., checksum]
 *    - Command 0x03 (8 bytes): Device/battery/light mode status -> ignored (returns null)
 *    - Command 0x24 (18 bytes): 14-byte bit-packed ANT+ Bike Radar Pages 0x30+0x31 for up to 8 targets
 * 1) Garmin Varia BLE Stream (0x6A4E3203):
 *    - Byte 0: Rolling sequence counter or header
 *    - Records (3 bytes per target): [Target ID, Distance (m), Speed (m/s)]
 * 2) Standard GATT Cycling Radar (0x2B18) & TR70 / Vendor:
 *    - [Count/ID, Threat Level, Distance] or [Distance, Speed, Threat Level]
 * 3) Multi-target chunk streams without headers:
 *    - 3-byte and 4-byte repeating records with direct or nibble bitfield threat levels.
 */
class RadarParser {

    private var lastLoggedPacketHex: String? = null

    /**
     * Parses raw BLE notification bytes into a [RadarData] snapshot containing vehicle threat list.
     *
     * @param data Raw notification payload, or null.
     * @return [RadarData] containing detected [RadarThreat] targets, or null if payload is null/empty or a non-radar status packet.
     */
    fun parse(data: ByteArray?): RadarData? {
        if (data == null || data.isEmpty()) return null

        val firstByte = data[0].toInt() and 0xFF
        val hexString = data.joinToString(" ") { "%02X".format(it) }

        // Coospo TR70 0xC8-framed protocol ([0xC8, length, commandId, ...payload..., checksum])
        if (firstByte == 0xC8 && data.size >= 4 && (data[1].toInt() and 0xFF) == data.size) {
            return parseCoospoC8Frame(data, hexString)
        }

        if (hexString != lastLoggedPacketHex) {
            lastLoggedPacketHex = hexString
            AppLogger.d(TAG, "Parsing raw BLE radar packet (len=${data.size}): [$hexString]")
        }
        AppLogger.setCustomKey("last_radar_packet_hex", hexString)

        val threats = mutableListOf<RadarThreat>()

        try {
            // Case 1: Pure single/double byte clear packets
            if (data.size == 1) {
                AppLogger.d(TAG, "Single byte radar packet -> Clear")
                AppLogger.setCustomKey("last_radar_threat_count", 0)
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            if (data.size == 2 && data[0].toInt() == 0 && data[1].toInt() == 0) {
                AppLogger.d(TAG, "2-byte zero packet -> Radar Clear")
                AppLogger.setCustomKey("last_radar_threat_count", 0)
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            // Case 2: Vendor prefix clear payload (e.g. 0xFA, 0x00)
            if (firstByte >= 0xF0 && data.size >= 2 && (data[1].toInt() and 0xFF) == 0) {
                AppLogger.d(TAG, "Vendor prefix indicates 0 targets (Radar Clear)")
                AppLogger.setCustomKey("last_radar_threat_count", 0)
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            val remainingLen = data.size - 1

            // Determine target count, start offset, and record chunk size
            val (targetCount, startOffset, recordSize) = when {
                // Header is magic Vendor prefix e.g. 0xFA, 0xAA with count in byte 1
                firstByte >= 0xF0 && data.size >= 2 -> {
                    val count = data[1].toInt() and 0xFF
                    if (count == 0) return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
                    val rSize = if ((data.size - 2) >= count * 4) 4 else 3
                    Triple(count, 2, rSize)
                }
                // Explicit count in byte 0 matching remaining length (4-byte or 3-byte records)
                firstByte in 1..10 && remainingLen == firstByte * 4 -> {
                    Triple(firstByte, 1, 4)
                }
                firstByte in 1..10 && remainingLen == firstByte * 3 -> {
                    Triple(firstByte, 1, 3)
                }
                // Garmin Varia style packet: 1-byte sequence counter/header + N * 3 bytes (len = 4, 7, 10, 13...)
                remainingLen > 0 && remainingLen % 3 == 0 -> {
                    Triple(remainingLen / 3, 1, 3)
                }
                // Entire payload is divisible chunks starting at index 0
                data.size % 4 == 0 -> Triple(data.size / 4, 0, 4)
                data.size % 3 == 0 -> Triple(data.size / 3, 0, 3)
                else -> {
                    val sOffset = if (firstByte in 1..10) 1 else 0
                    val count = ((data.size - sOffset) / 3).coerceAtLeast(1)
                    Triple(count, sOffset, 3)
                }
            }

            if (targetCount == 0) {
                AppLogger.d(TAG, "Target count is 0 (Radar Clear)")
                AppLogger.setCustomKey("last_radar_threat_count", 0)
                return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
            }

            var offset = startOffset
            var targetId = 1

            while (offset + 1 < data.size && threats.size < targetCount) {
                val b0 = data[offset].toInt() and 0xFF
                val b1 = data[offset + 1].toInt() and 0xFF

                if (recordSize >= 4 && offset + 3 < data.size) {
                    // 4-byte chunk: [b0, b1, b2, b3]
                    val b2 = data[offset + 2].toInt() and 0xFF
                    val b3 = data[offset + 3].toInt() and 0xFF
                    offset += 4

                    val t1 = decodeThreatLevel(b1)
                    val (threatLevel, dist, speed, id) = if (t1 != ThreatLevel.NONE) {
                        Quad(t1, b2.toFloat().coerceIn(0f, 200f), (b3 * 3.6f).coerceIn(0f, 200f), if (b0 > 0) b0 else targetId++)
                    } else {
                        val t2 = decodeThreatLevel(b2)
                        Quad(t2, b1.toFloat().coerceIn(0f, 200f), (b3 * 3.6f).coerceIn(0f, 200f), if (b0 > 0) b0 else targetId++)
                    }

                    if (threatLevel != ThreatLevel.NONE) {
                        threats.add(
                            RadarThreat(
                                id = id,
                                threatLevel = threatLevel,
                                distanceMeters = dist,
                                speedKmH = speed
                            )
                        )
                    }
                    continue
                }

                if (offset + 2 < data.size) {
                    // 3-byte chunk: [b0, b1, b2]
                    val b2 = data[offset + 2].toInt() and 0xFF
                    offset += 3

                    val t0 = decodeThreatLevel(b0)
                    val t1 = decodeThreatLevel(b1)
                    val t2 = decodeThreatLevel(b2)

                    val (threatLevel, distanceMeters, speedKmH, idVal) = when {
                        // Pattern 1: [ID in 1..15, Threat in 1..2, Distance in 0..2] -> Close range TR70 (0m..2m)
                        b0 in 1..15 && t1 != ThreatLevel.NONE && b1 in 1..2 && b2 in 0..2 -> {
                            Quad(t1, b2.toFloat().coerceIn(0f, 200f), 0f, b0)
                        }

                        // Pattern 2: [ID in 1..15, Threat Level, Distance > 2] -> Standard TR70 (e.g. [1, 1, 80], [1, 1, 18])
                        b2 > 2 && t1 != ThreatLevel.NONE && b1 in 1..2 && (b0 in 1..15 || t0 != ThreatLevel.NONE) -> {
                            val d = b2.toFloat().coerceIn(0f, 200f)
                            val id = if (b0 in 1..15) b0 else targetId++
                            Quad(t1, d, 0f, id)
                        }

                        // Pattern 3: [Distance, Speed, Threat Level in 1..2] -> Coospo TR70 (e.g. [80, 10, 1], [14, 10, 1], [2, 5, 1], [0, 5, 1])
                        t2 != ThreatLevel.NONE && b2 in 1..2 && b1 <= 25 -> {
                            val d = b0.toFloat().coerceIn(0f, 200f)
                            val s = (b1 * 3.6f).coerceIn(0f, 200f)
                            Quad(t2, d, s, targetId++)
                        }

                        // Pattern 4: Explicit target clear packet [ID in 1..15, Distance > 20, Threat = 0] -> e.g. [1, 38, 0] Clear
                        b2 == 0 && b1 > 20 && b0 in 1..15 -> {
                            val d = b1.toFloat().coerceIn(0f, 200f)
                            Quad(ThreatLevel.NONE, d, 0f, b0)
                        }

                        // Pattern 5: [Threat Level, Distance, Speed] where b0 is threat level and b1 is distance (e.g. [1, 45, 12], [2, 30, 10])
                        t0 != ThreatLevel.NONE && b0 in 1..2 && b1 > 0 -> {
                            val d = b1.toFloat().coerceIn(0f, 200f)
                            val s = (b2 * 3.6f).coerceIn(0f, 200f)
                            Quad(t0, d, s, targetId++)
                        }

                        // Pattern 6: Garmin Varia raw target chunk [Target ID, Distance, Speed] where threat is speed-derived
                        b0 in 0..15 && b1 > 0 -> {
                            val d = b1.toFloat().coerceIn(0f, 200f)
                            val s = (b2 * 3.6f).coerceIn(0f, 200f)
                            val t = when {
                                s > 54f -> ThreatLevel.HIGH_SPEED
                                s > 0f -> ThreatLevel.APPROACHING
                                else -> ThreatLevel.NONE
                            }
                            val id = if (b0 > 0) b0 else targetId++
                            Quad(t, d, s, id)
                        }

                        // Fallback: search for threat level byte among b0, b1, b2
                        else -> {
                            val t = if (t0 != ThreatLevel.NONE) t0 else if (t1 != ThreatLevel.NONE) t1 else t2
                            val d = maxOf(b1, b2).toFloat().coerceIn(0f, 200f)
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
                    // 2-byte chunk fallback [Threat Level, Distance]
                    val threatLevel = decodeThreatLevel(b0)
                    val dist = b1.toFloat().coerceIn(0f, 200f)
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
            AppLogger.e(TAG, "Error parsing radar notification payload", t)
        }

        val activeThreats = threats.filter { it.threatLevel != ThreatLevel.NONE }
        AppLogger.d(TAG, "Parsed RadarData -> total targets: ${threats.size}, active threats: ${activeThreats.size}, details: $activeThreats")
        AppLogger.setCustomKey("last_radar_threat_count", activeThreats.size)

        return RadarData(
            threats = activeThreats,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Parses Coospo TR70 0xC8-framed notifications on characteristic 0xFDB1.
     *
     * Frame layout: `[0xC8, length, commandId, ...payload..., checksum]`
     * - `commandId != 0x24` (e.g. `0x03` 8-byte battery/light status frame): returns `null` so status packets
     *   do not overwrite active radar threats or reset the radar stale-data watchdog.
     * - `commandId == 0x24` (18-byte radar targets frame): `data[3..16]` contains 14 bytes of bit-packed
     *   ANT+ Bike Radar (Pages 0x30 + 0x31) data for up to 8 targets:
     *   - `data[3..4]`: 2-bit threat levels for targets 0..7 (`0` = None, `1` = Approaching, `2` = Fast Approaching)
     *   - `data[5..6]`: 2-bit threat sides for targets 0..7
     *   - `data[7..9]`: 24-bit big-endian packed 6-bit ranges for targets 0..3 (`LSB = 3.125 m`)
     *   - `data[10..12]`: 24-bit big-endian packed 6-bit ranges for targets 4..7 (`LSB = 3.125 m`)
     *   - `data[13..16]`: 4-bit nibbles of closing speed for targets 0..7 (`LSB = 3.04 m/s = 10.944 km/h`)
     */
    private fun parseCoospoC8Frame(data: ByteArray, hexString: String): RadarData? {
        val commandId = data[2].toInt() and 0xFF
        if (commandId != 0x24) {
            // Non-radar status/battery/light-mode packet (e.g., 0x03) -> ignore completely
            return null
        }
        if (data.size < 18) return null

        val isChanged = hexString != lastLoggedPacketHex
        if (isChanged) {
            lastLoggedPacketHex = hexString
            AppLogger.d(TAG, "Parsing Coospo 0xC8 radar packet (len=${data.size}): [$hexString]")
        }
        AppLogger.setCustomKey("last_radar_packet_hex", hexString)

        // Fast path: all 14 payload bytes (indices 3..16) are zero -> road is clear
        if ((3..16).all { (data[it].toInt() and 0xFF) == 0 }) {
            if (isChanged) {
                AppLogger.d(TAG, "Coospo 0xC8 0x24 radar packet -> 0 active threats (Clear)")
            }
            AppLogger.setCustomKey("last_radar_threat_count", 0)
            return RadarData(threats = emptyList(), timestamp = System.currentTimeMillis())
        }

        val threats = mutableListOf<RadarThreat>()
        val threatsBits = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
        val rangeChunkA = ((data[7].toInt() and 0xFF) shl 16) or
                ((data[8].toInt() and 0xFF) shl 8) or
                (data[9].toInt() and 0xFF)
        val rangeChunkB = ((data[10].toInt() and 0xFF) shl 16) or
                ((data[11].toInt() and 0xFF) shl 8) or
                (data[12].toInt() and 0xFF)

        for (i in 0 until 8) {
            val threatCode = (threatsBits shr (2 * i)) and 0x03
            val threatLevel = when (threatCode) {
                1 -> ThreatLevel.APPROACHING
                2, 3 -> ThreatLevel.HIGH_SPEED
                else -> ThreatLevel.NONE
            }
            if (threatLevel == ThreatLevel.NONE) continue

            val rawRange = if (i < 4) {
                (rangeChunkA shr (6 * i)) and 0x3F
            } else {
                (rangeChunkB shr (6 * (i - 4))) and 0x3F
            }
            val distanceMeters = (rawRange * 3.125f).coerceIn(0f, 200f)

            val speedByte = data[13 + i / 2].toInt() and 0xFF
            val rawSpeed = (speedByte shr ((i % 2) * 4)) and 0x0F
            val speedKmH = if (rawSpeed in 1..14) {
                (rawSpeed * 3.04f * 3.6f).coerceIn(0f, 200f)
            } else {
                0f
            }

            threats.add(
                RadarThreat(
                    id = i + 1,
                    threatLevel = threatLevel,
                    distanceMeters = distanceMeters,
                    speedKmH = speedKmH
                )
            )
        }

        if (isChanged) {
            AppLogger.d(
                TAG,
                "Parsed Coospo 0xC8 RadarData -> active threats: ${threats.size}, details: $threats"
            )
        }
        AppLogger.setCustomKey("last_radar_threat_count", threats.size)

        return RadarData(
            threats = threats,
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

        // Check lower nibble (bits 0..2) - ANT+ and standard cycling radar combines Target ID (bits 4..7) and Threat (bits 0..2)
        val lowerNibble = rawByte and 0x07
        if (lowerNibble in 1..2) return ThreatLevel.fromValue(lowerNibble)

        // Check upper nibble (bits 4..6) - vendors shifting threat level to upper nibble
        val upperNibble = (rawByte shr 4) and 0x07
        if (upperNibble in 1..2) return ThreatLevel.fromValue(upperNibble)

        return ThreatLevel.NONE
    }

    companion object {
        private const val TAG = "RadarParser"
    }
}
