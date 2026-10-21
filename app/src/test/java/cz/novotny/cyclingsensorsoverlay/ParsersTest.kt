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
}
