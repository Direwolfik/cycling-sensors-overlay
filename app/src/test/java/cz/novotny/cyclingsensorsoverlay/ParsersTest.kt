package cz.novotny.cyclingsensorsoverlay

import cz.novotny.cyclingsensorsoverlay.data.ble.parser.CyclingPowerParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.HeartRateParser
import cz.novotny.cyclingsensorsoverlay.data.ble.parser.RadarParser
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
            0x01.toByte(),
            0x02.toByte(),
            30.toByte(),
            10.toByte()
        )

        val result = parser.parse(payload)
        assertNotNull(result)
        assertEquals(1, result?.threats?.size)
        val threat = result?.threats?.first()
        assertEquals(2, threat?.threatLevel)
        assertEquals(30f, threat?.distanceMeters)
        assertEquals(36f, threat?.speedKmH ?: 0f, 0.1f)
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

        // 2-byte payload
        val twoByteResult = parser.parse(byteArrayOf(0x01, 0x02))
        assertNotNull(twoByteResult)
        assertTrue(twoByteResult!!.threats.isEmpty())

        // Malformed odd length payload (e.g. 5 bytes)
        val oddResult = parser.parse(byteArrayOf(0x01.toByte(), 0x02.toByte(), 40.toByte(), 15.toByte(), 99.toByte()))
        assertNotNull(oddResult)
        assertEquals(1, oddResult!!.threats.size)
        assertEquals(40f, oddResult.threats[0].distanceMeters)

        // Large random payload without crash
        val largeRandom = ByteArray(50) { it.toByte() }
        val largeResult = parser.parse(largeRandom)
        assertNotNull(largeResult)
    }
}
