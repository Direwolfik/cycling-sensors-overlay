package cz.novotny.cyclingsensorsoverlay.data.ble.parser

import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData

/**
 * Binary parser for Bluetooth Low Energy Cycling Power Measurement notifications (GATT Characteristic 0x2A63 / Service 0x1818).
 *
 * Extract instantaneous power in Watts and calculates pedaling cadence in RPM from cumulative crank revolution
 * data when available.
 *
 * Payload structure:
 * - Bytes 0..1: 16-bit flags bitmask indicating presence of optional fields.
 * - Bytes 2..3: Signed 16-bit instantaneous power in Watts (coerced to >= 0).
 * - Optional offset fields:
 *   - Pedal Power Balance (1 byte, flag bit 0)
 *   - Accumulated Torque (2 bytes, flag bit 2)
 *   - Wheel Revolution Data (6 bytes, flag bit 4)
 *   - Crank Revolution Data (4 bytes: uint16 cumulative revs + uint16 1/1024s event time, flag bit 5)
 *
 * Cadence calculation:
 * Computes delta crank revolutions divided by delta event time (scaled to seconds), converting to RPM.
 * Corrects for 16-bit uint16 counter rollover at 65536 and rejects interval anomalies outside 0.1s..3.0s.
 */
class CyclingPowerParser {
    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null
    private var lastWheelRevolutions: Long? = null
    private var lastWheelEventTime: Int? = null

    /**
     * Parses raw BLE notification bytes into a [PowerData] domain object.
     *
     * @param data Raw byte array from characteristic update notification.
     * @return [PowerData] containing instantaneous power, cadence, and speed, or null if payload is invalid/truncated.
     */
    fun parse(data: ByteArray): PowerData? {
        if (data.size < 4) return null

        val flags = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)

        // Instantaneous power is sint16 at bytes 2,3
        val powerRaw = (data[2].toInt() and 0xFF) or (data[3].toInt() shl 8)
        val instantaneousPower = powerRaw.toShort().toInt().coerceAtLeast(0)

        var offset = 4
        val pedalBalancePresent = (flags and 0x0001) != 0
        val accumTorquePresent = (flags and 0x0004) != 0
        val wheelRevPresent = (flags and 0x0010) != 0
        val crankRevPresent = (flags and 0x0020) != 0

        if (pedalBalancePresent) offset += 1
        if (accumTorquePresent) offset += 2

        var speedKmh: Float? = null
        if (wheelRevPresent && data.size >= offset + 6) {
            val wheelRevs = (data[offset].toLong() and 0xFFL) or
                    ((data[offset + 1].toLong() and 0xFFL) shl 8) or
                    ((data[offset + 2].toLong() and 0xFFL) shl 16) or
                    ((data[offset + 3].toLong() and 0xFFL) shl 24)
            val wheelTime = (data[offset + 4].toInt() and 0xFF) or
                    ((data[offset + 5].toInt() and 0xFF) shl 8)

            val prevRevs = lastWheelRevolutions
            val prevTime = lastWheelEventTime

            if (prevRevs != null && prevTime != null) {
                var revsDiff = wheelRevs - prevRevs
                if (revsDiff < 0) revsDiff += 4294967296L

                var timeDiff = wheelTime - prevTime
                if (timeDiff < 0) timeDiff += 65536

                if (timeDiff > 0 && revsDiff >= 0) {
                    val timeSec = timeDiff / 2048.0
                    if (timeSec in 0.1..3.0) {
                        val distanceMeters = revsDiff * 2.133f
                        val mps = distanceMeters / timeSec
                        speedKmh = (mps * 3.6).toFloat().coerceIn(0f, 120f)
                    }
                }
            }
            lastWheelRevolutions = wheelRevs
            lastWheelEventTime = wheelTime
            offset += 6
        }

        var cadence: Int? = null
        if (crankRevPresent && data.size >= offset + 4) {
            val crankRevs = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val crankTime = (data[offset + 2].toInt() and 0xFF) or ((data[offset + 3].toInt() and 0xFF) shl 8)

            val prevRevs = lastCrankRevolutions
            val prevTime = lastCrankEventTime

            if (prevRevs != null && prevTime != null) {
                var revsDiff = crankRevs - prevRevs
                if (revsDiff < 0) revsDiff += 65536

                var timeDiff = crankTime - prevTime
                if (timeDiff < 0) timeDiff += 65536

                if (timeDiff > 0 && revsDiff >= 0) {
                    val timeSec = timeDiff / 1024.0
                    if (timeSec in 0.1..3.0) {
                        val rpm = ((revsDiff / timeSec) * 60.0).toInt()
                        cadence = rpm.coerceIn(0, 250)
                    }
                }
            }
            lastCrankRevolutions = crankRevs
            lastCrankEventTime = crankTime
        }

        return PowerData(
            instantaneousPower = instantaneousPower,
            cadence = cadence,
            speedKmh = speedKmh,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Resets internal crank and wheel revolution tracking state.
     */
    fun reset() {
        lastCrankRevolutions = null
        lastCrankEventTime = null
        lastWheelRevolutions = null
        lastWheelEventTime = null
    }
}
