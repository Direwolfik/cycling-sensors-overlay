package cz.novotny.cyclingsensorsoverlay

import cz.novotny.cyclingsensorsoverlay.data.ble.handler.HeartRateSensorHandler
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.PowerSensorHandler
import cz.novotny.cyclingsensorsoverlay.data.ble.handler.RadarSensorHandler
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleSensorHandlersTest {

    @Test
    fun powerSensorHandler_processesDataAndResetsOnDisconnect() {
        val handler = PowerSensorHandler()
        assertEquals(SensorType.POWER, handler.sensorType)
        assertNull(handler.dataStream.value)

        val packet = byteArrayOf(
            0x20.toByte(), 0x00.toByte(),
            0xFA.toByte(), 0x00.toByte(),
            100.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x04.toByte()
        )

        handler.onDataReceived(mockChar(), packet)
        assertNotNull(handler.dataStream.value)
        assertEquals(250, handler.dataStream.value?.instantaneousPower)

        handler.onDisconnected()
        assertNull(handler.dataStream.value)
    }

    @Test
    fun heartRateSensorHandler_processesDataAndResetsOnDisconnect() {
        val handler = HeartRateSensorHandler()
        assertEquals(SensorType.HEART_RATE, handler.sensorType)
        assertNull(handler.dataStream.value)

        val packet = byteArrayOf(0x00, 150.toByte())
        handler.onDataReceived(mockChar(), packet)
        assertNotNull(handler.dataStream.value)
        assertEquals(150, handler.dataStream.value?.bpm)

        handler.onDisconnected()
        assertNull(handler.dataStream.value)
    }

    @Test
    fun radarSensorHandler_processesDataAndSimulation() = runTest {
        val handler = RadarSensorHandler(scope = this)
        assertEquals(SensorType.RADAR, handler.sensorType)
        assertFalse(handler.isRadarSimulated.value)

        val payload = byteArrayOf(
            0x01.toByte(),
            0x02.toByte(),
            30.toByte(),
            10.toByte()
        )

        handler.onDataReceived(mockChar(), payload)
        assertNotNull(handler.dataStream.value)
        assertEquals(1, handler.dataStream.value?.threats?.size)
        assertEquals(ThreatLevel.HIGH_SPEED, handler.dataStream.value?.threats?.first()?.threatLevel)

        handler.onDisconnected()
        assertNull(handler.dataStream.value)

        handler.setRadarSimulationEnabled(true)
        assertTrue(handler.isRadarSimulated.value)

        testScheduler.advanceTimeBy(200L)
        assertNotNull(handler.dataStream.value)
        assertTrue(handler.dataStream.value?.threats?.isNotEmpty() == true)

        handler.setRadarSimulationEnabled(false)
        assertFalse(handler.isRadarSimulated.value)
        assertNull(handler.dataStream.value)
    }

    private fun mockChar(): android.bluetooth.BluetoothGattCharacteristic {
        return android.bluetooth.BluetoothGattCharacteristic(
            java.util.UUID.randomUUID(),
            0,
            0
        )
    }
}
