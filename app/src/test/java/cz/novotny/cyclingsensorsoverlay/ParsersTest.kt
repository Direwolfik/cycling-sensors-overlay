package cz.novotny.cyclingsensorsoverlay

import cz.novotny.cyclingsensorsoverlay.data.ble.parser.CyclingPowerParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.HeartRateParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.RadarParser
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import org.junit.Assert.*
import org.junit.Test

class ParsersTest {

    @Test
    fun heartRateParser_parsesUint8AndUint16() {
        val parser = HeartRateParser()

        val data8Bit = byteArrayOf(0x00, 145.toByte())
        val result8 = parser.parse(data8Bit)
        assertNotNull(result8)
        assertEquals(145, result8?.bpm)

        val data16Bit = byteArrayOf(0x01, 180.toByte(), 0x00)
        val result16 = parser.parse(data16Bit)
        assertNotNull(result16)
        assertEquals(180, result16?.bpm)
    }

    @Test
    fun cyclingPowerParser_parsesInstantaneousPowerAndCadence() {
        val parser = CyclingPowerParser()

        val data1 = byteArrayOf(
            0x20.toByte(), 0x00.toByte(),
            0xFA.toByte(), 0x00.toByte(),
            100.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x04.toByte()
        )

        val res1 = parser.parse(data1)
        assertNotNull(res1)
        assertEquals(250, res1?.instantaneousPower)
        assertNull(res1?.cadence)

        val data2 = byteArrayOf(
            0x20.toByte(), 0x00.toByte(),
            0x04.toByte(), 0x01.toByte(), // 260 Watts (0x0104)
            101.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x08.toByte()
        )

        val res2 = parser.parse(data2)
        assertNotNull(res2)
        assertEquals(260, res2?.instantaneousPower)
        assertEquals(60, res2?.cadence)
    }

    @Test
    fun radarParser_parsesThreatsCorrectly() {
        val parser = RadarParser()

        val payload = byteArrayOf(
            0x01.toByte(), // Header: 1 target
            0x02.toByte(), // Threat level: 2 (High speed red)
            30.toByte(),   // Distance: 30 meters
            10.toByte()    // Speed: 10 m/s
        )

        val result = parser.parse(payload)
        assertNotNull(result)
        assertEquals(1, result?.threats?.size)
        val threat = result?.threats?.first()
        assertEquals(ThreatLevel.HIGH_SPEED, threat?.threatLevel)
        assertEquals(30f, threat?.distanceMeters)
        assertEquals(36f, threat?.speedKmH ?: 0f, 0.1f)
    }

    @Test
    fun radarParser_parsesMultiTargetWithoutHeaderAndBitfieldThreats() {
        val parser = RadarParser()

        // 2 targets, 6 bytes total without count header:
        // Target 1: threat=1 (yellow), dist=40m, speed=5m/s (18 km/h)
        // Target 2: threat=0x20 (upper nibble 2 = high speed red), dist=15m, speed=10m/s (36 km/h)
        val payload = byteArrayOf(
            0x01.toByte(), 40.toByte(), 5.toByte(),
            0x20.toByte(), 15.toByte(), 10.toByte()
        )

        val result = parser.parse(payload)
        assertNotNull(result)
        assertEquals(2, result?.threats?.size)

        val t1 = result?.threats?.get(0)
        assertEquals(ThreatLevel.APPROACHING, t1?.threatLevel)
        assertEquals(40f, t1?.distanceMeters)

        val t2 = result?.threats?.get(1)
        assertEquals(ThreatLevel.HIGH_SPEED, t2?.threatLevel)
        assertEquals(15f, t2?.distanceMeters)
    }

    @Test
    fun radarParser_parsesTR70AndVariaPayloadsAccurately() {
        val parser = RadarParser()

        // 3-byte record with explicit count header = 1: ID=1, Threat=1 (Approaching), Distance=80m
        val tr70Payload80m = byteArrayOf(
            0x01.toByte(), // Target count: 1
            0x01.toByte(), // Threat level: 1 (Approaching yellow)
            80.toByte()    // Distance: 80 meters
        )

        val res80m = parser.parse(tr70Payload80m)
        assertNotNull(res80m)
        assertEquals(1, res80m?.threats?.size)
        val t80 = res80m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t80?.threatLevel)
        assertEquals(80f, t80?.distanceMeters)

        // 3-byte record with explicit count header = 1: ID=1, Threat=1, Distance=18m
        val tr70Payload18m = byteArrayOf(
            0x01.toByte(), // Target count: 1
            0x01.toByte(), // Threat level: 1 (Approaching yellow)
            18.toByte()    // Distance: 18 meters
        )

        val res18m = parser.parse(tr70Payload18m)
        assertNotNull(res18m)
        assertEquals(1, res18m?.threats?.size)
        val t18 = res18m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t18?.threatLevel)
        assertEquals(18f, t18?.distanceMeters)
    }

    @Test
    fun radarParser_parsesCoospoPayloadsWithDistanceAsFirstByteInChunk() {
        val parser = RadarParser()

        // 3-byte chunk stream where b0=80 (Dist=80m), b1=10 (Speed=10m/s=36km/h), b2=1 (Threat=Approaching)
        val payload80m = byteArrayOf(80.toByte(), 10.toByte(), 0x01.toByte())
        val res = parser.parse(payload80m)
        assertNotNull(res)
        assertEquals(1, res?.threats?.size)
        val t = res?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t?.threatLevel)
        assertEquals(80f, t?.distanceMeters)
        assertEquals(36f, t?.speedKmH ?: 0f, 0.1f)
    }

    @Test
    fun radarParser_parsesCloseRangeThreatsAccurately() {
        val parser = RadarParser()

        // 1. Coospo close range at 14m: [14m, 10m/s, Threat 1]
        val coospo14m = byteArrayOf(14.toByte(), 10.toByte(), 0x01.toByte())
        val res14m = parser.parse(coospo14m)
        assertNotNull(res14m)
        assertEquals(1, res14m?.threats?.size)
        val t14 = res14m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t14?.threatLevel)
        assertEquals(14f, t14?.distanceMeters)
        assertEquals(36f, t14?.speedKmH ?: 0f, 0.1f)

        // 2. Coospo close range at 2m: [2m, 5m/s, Threat 1]
        val coospo2m = byteArrayOf(2.toByte(), 5.toByte(), 0x01.toByte())
        val res2m = parser.parse(coospo2m)
        assertNotNull(res2m)
        assertEquals(1, res2m?.threats?.size)
        val t2 = res2m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t2?.threatLevel)
        assertEquals(2f, t2?.distanceMeters)
        assertEquals(18f, t2?.speedKmH ?: 0f, 0.1f)

        // 3. Coospo immediate contact at 0m: [0m, 5m/s, Threat 1]
        val coospo0m = byteArrayOf(0.toByte(), 5.toByte(), 0x01.toByte())
        val res0m = parser.parse(coospo0m)
        assertNotNull(res0m)
        assertEquals(1, res0m?.threats?.size)
        val t0 = res0m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, t0?.threatLevel)
        assertEquals(0f, t0?.distanceMeters)

        // 4. TR70 close range at 2m: [ID=1, Threat=1, Dist=2m]
        val tr702m = byteArrayOf(0x01.toByte(), 0x01.toByte(), 2.toByte())
        val resTr702m = parser.parse(tr702m)
        assertNotNull(resTr702m)
        assertEquals(1, resTr702m?.threats?.size)
        val tTr2 = resTr702m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, tTr2?.threatLevel)
        assertEquals(2f, tTr2?.distanceMeters)

        // 5. TR70 immediate contact at 0m: [ID=1, Threat=1, Dist=0m]
        val tr700m = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0.toByte())
        val resTr700m = parser.parse(tr700m)
        assertNotNull(resTr700m)
        assertEquals(1, resTr700m?.threats?.size)
        val tTr0 = resTr700m?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, tTr0?.threatLevel)
        assertEquals(0f, tTr0?.distanceMeters)

        // 6. TR70 high speed close range at 1m: [ID=1, Threat=2, Dist=1m]
        val tr701mHigh = byteArrayOf(0x01.toByte(), 0x02.toByte(), 1.toByte())
        val resTr701m = parser.parse(tr701mHigh)
        assertNotNull(resTr701m)
        assertEquals(1, resTr701m?.threats?.size)
        val tTr1 = resTr701m?.threats?.first()
        assertEquals(ThreatLevel.HIGH_SPEED, tTr1?.threatLevel)
        assertEquals(1f, tTr1?.distanceMeters)
    }

    @Test
    fun radarParser_parsesGarminVariaRollingSequencesAndExtendedRange() {
        val parser = RadarParser()

        // 1. Garmin Varia 4-byte packet with rolling counter 0x00
        val variaRollover0 = byteArrayOf(0x00.toByte(), 0x01.toByte(), 45.toByte(), 12.toByte())
        val resRollover = parser.parse(variaRollover0)
        assertNotNull(resRollover)
        assertEquals(1, resRollover?.threats?.size)
        val tRollover = resRollover?.threats?.first()
        assertEquals(45f, tRollover?.distanceMeters)

        // 2. Garmin Varia 4-byte packet with rolling counter 15 (>10)
        val variaSeq15 = byteArrayOf(15.toByte(), 0x01.toByte(), 45.toByte(), 12.toByte())
        val resSeq15 = parser.parse(variaSeq15)
        assertNotNull(resSeq15)
        assertEquals(1, resSeq15?.threats?.size)
        val tSeq15 = resSeq15?.threats?.first()
        assertEquals(45f, tSeq15?.distanceMeters)

        // 3. Extended detection range up to 180m (not truncated to 150m)
        val extendedRangePayload = byteArrayOf(180.toByte(), 10.toByte(), 0x01.toByte())
        val resExtended = parser.parse(extendedRangePayload)
        assertNotNull(resExtended)
        assertEquals(1, resExtended?.threats?.size)
        val tExt = resExtended?.threats?.first()
        assertEquals(180f, tExt?.distanceMeters)
    }

    @Test
    fun radarParser_parsesAntPlusCombinedNibbleThreats() {
        val parser = RadarParser()

        // 1. 3-byte record with Target 1 and Approaching (0x11 = upper nibble 1, lower nibble 1), 60m, speed 8 m/s
        val antApproaching = byteArrayOf(0x11.toByte(), 60.toByte(), 8.toByte())
        val resApp = parser.parse(antApproaching)
        assertNotNull(resApp)
        assertEquals(1, resApp?.threats?.size)
        val tApp = resApp?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, tApp?.threatLevel)
        assertEquals(60f, tApp?.distanceMeters)

        // 2. 3-byte record with Target 2 and High Speed (0x22 = upper nibble 2, lower nibble 2), 90m, speed 20 m/s
        val antHighSpeed = byteArrayOf(0x22.toByte(), 90.toByte(), 20.toByte())
        val resHigh = parser.parse(antHighSpeed)
        assertNotNull(resHigh)
        assertEquals(1, resHigh?.threats?.size)
        val tHigh = resHigh?.threats?.first()
        assertEquals(ThreatLevel.HIGH_SPEED, tHigh?.threatLevel)
        assertEquals(90f, tHigh?.distanceMeters)

        // 3. Low relative speed approaching target (e.g. car matching speed at 50m: [1, 50, 2])
        val lowSpeedApproaching = byteArrayOf(0x01.toByte(), 50.toByte(), 2.toByte())
        val resLow = parser.parse(lowSpeedApproaching)
        assertNotNull(resLow)
        assertEquals(1, resLow?.threats?.size)
        val tLow = resLow?.threats?.first()
        assertEquals(ThreatLevel.APPROACHING, tLow?.threatLevel)
        assertEquals(50f, tLow?.distanceMeters)
    }

    @Test
    fun radarParser_handlesMultiByteClearPackets() {
        val parser = RadarParser()

        // 2-byte clear payload: count = 0
        val clear2Byte = byteArrayOf(0x00, 0x00)
        val res2Byte = parser.parse(clear2Byte)
        assertNotNull(res2Byte)
        assertTrue(res2Byte!!.threats.isEmpty())

        // Vendor prefix clear payload: 0xFA (prefix), 0x00 (0 targets)
        val vendorClear = byteArrayOf(0xFA.toByte(), 0x00)
        val resVendor = parser.parse(vendorClear)
        assertNotNull(resVendor)
        assertTrue(resVendor!!.threats.isEmpty())

        // 3-byte payload with Threat Level = 0 (NONE/Clear) at 38 meters
        val clear38mPayload = byteArrayOf(0x01.toByte(), 38.toByte(), 0x00.toByte())
        val res38mClear = parser.parse(clear38mPayload)
        assertNotNull(res38mClear)
        assertTrue("Threat level 0 at 38m must be cleared (0 active threats)", res38mClear!!.threats.isEmpty())
    }

    @Test
    fun radarParser_handlesEdgeCasesAndMalformedPayloads() {
        val parser = RadarParser()

        // Null payload
        assertNull(parser.parse(null))

        // Empty payload
        assertNull(parser.parse(byteArrayOf()))

        // Header = 0 (No threats)
        val clearResult = parser.parse(byteArrayOf(0x00))
        assertNotNull(clearResult)
        assertTrue(clearResult!!.threats.isEmpty())

        // Single byte non-zero payload
        val singleByteResult = parser.parse(byteArrayOf(0x05))
        assertNotNull(singleByteResult)
        assertTrue(singleByteResult!!.threats.isEmpty())

        // Large random payload without crash
        val largeRandom = ByteArray(50) { it.toByte() }
        val largeResult = parser.parse(largeRandom)
        assertNotNull(largeResult)
    }

    @Test
    fun radarParser_parsesCoospoTr70C8FramedPacketsWithoutPhantomVehicles() {
        val parser = RadarParser()

        // 1. Coospo TR70 0x03 status/battery packet (8 bytes): must return null so active threats are not wiped
        val statusPacket = hexToBytes("C8 08 03 02 45 55 0B 7A")
        assertNull("Status packet 0x03 must be ignored (return null)", parser.parse(statusPacket))

        // 2. Coospo TR70 0x24 empty-road clear packet (18 bytes): must return 0 threats (no phantom 36m vehicle)
        val clearPacket = hexToBytes("C8 12 24 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FE")
        val clearResult = parser.parse(clearPacket)
        assertNotNull(clearResult)
        assertTrue("Clear 0x24 packet must produce 0 threats", clearResult!!.threats.isEmpty())

        // 3. Single approaching vehicle at 21.875m (7 * 3.125m), speed 10.944 km/h (1 * 3.04 m/s)
        val singleTargetPacket = hexToBytes("C8 12 24 01 00 00 00 00 00 07 00 00 00 01 00 00 00 07")
        val singleResult = parser.parse(singleTargetPacket)
        assertNotNull(singleResult)
        assertEquals(1, singleResult!!.threats.size)
        val t1 = singleResult.threats[0]
        assertEquals(1, t1.id)
        assertEquals(ThreatLevel.APPROACHING, t1.threatLevel)
        assertEquals(21.875f, t1.distanceMeters, 0.01f)
        assertEquals(10.944f, t1.speedKmH, 0.05f)

        // 4. Two approaching vehicles at 18.75m (6 * 3.125m) and 43.75m (14 * 3.125m), checksum 0x99 must not create phantom 153m vehicle
        val twoTargetsPacket = hexToBytes("C8 12 24 05 00 00 00 00 03 86 00 00 00 11 00 00 00 99")
        val twoResult = parser.parse(twoTargetsPacket)
        assertNotNull(twoResult)
        assertEquals(2, twoResult!!.threats.size)
        assertEquals(18.75f, twoResult.threats[0].distanceMeters, 0.01f)
        assertEquals(43.75f, twoResult.threats[1].distanceMeters, 0.01f)

        // 5. High-speed vehicle at 9.375m (3 * 3.125m), speed 32.832 km/h (3 * 3.04 m/s)
        val highSpeedPacket = hexToBytes("C8 12 24 02 00 00 00 00 00 03 00 00 00 03 00 00 00 06")
        val highSpeedResult = parser.parse(highSpeedPacket)
        assertNotNull(highSpeedResult)
        assertEquals(1, highSpeedResult!!.threats.size)
        assertEquals(ThreatLevel.HIGH_SPEED, highSpeedResult.threats[0].threatLevel)
        assertEquals(9.375f, highSpeedResult.threats[0].distanceMeters, 0.01f)
        assertEquals(32.832f, highSpeedResult.threats[0].speedKmH, 0.05f)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.trim().split("\\s+".toRegex()).map { it.toInt(16).toByte() }.toByteArray()
    }
}
