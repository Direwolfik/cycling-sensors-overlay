package cz.novotny.cyclingsensorsoverlay

import android.content.Context
import android.content.ContextWrapper
import cz.novotny.cyclingsensorsoverlay.data.location.GpsLocationManager
import cz.novotny.cyclingsensorsoverlay.domain.model.ConnectionState
import cz.novotny.cyclingsensorsoverlay.domain.model.DiscoveredDevice
import cz.novotny.cyclingsensorsoverlay.domain.model.HeartRateData
import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBleRepository: FakeBleRepository
    private lateinit var observeSensorDataUseCase: ObserveSensorDataUseCase
    private lateinit var gpsLocationManager: GpsLocationManager
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
        fakeBleRepository = FakeBleRepository()
        gpsLocationManager = GpsLocationManager(dummyContext)
        observeSensorDataUseCase = ObserveSensorDataUseCase(fakeBleRepository, gpsLocationManager)
        viewModel = DashboardViewModel(observeSensorDataUseCase, fakeBleRepository, gpsLocationManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observeSensorData_updatesDashboardSensorState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.sensorState.collect {}
        }

        val now = System.currentTimeMillis()
        fakeBleRepository.powerDataStream.value = PowerData(instantaneousPower = 250, cadence = 90, timestamp = now)
        fakeBleRepository.heartRateDataStream.value = HeartRateData(bpm = 155, timestamp = now)
        fakeBleRepository.radarDataStream.value = RadarData(
            threats = listOf(RadarThreat(id = 1, threatLevel = ThreatLevel.HIGH_SPEED, distanceMeters = 30f, speedKmH = 60f))
        )
        advanceUntilIdle()

        val state = viewModel.sensorState.value
        assertNotNull(state.powerData)
        assertEquals(250, state.powerData?.instantaneousPower)
        assertEquals(90, state.powerData?.cadence)
        assertEquals(155, state.heartRateData?.bpm)
        assertEquals(1, state.radarData?.threats?.size)
        assertEquals(ThreatLevel.HIGH_SPEED, state.radarData?.threats?.first()?.threatLevel)
    }

    @Test
    fun toggleRadarSimulation_togglesSimulationState() = runTest {
        assertFalse(viewModel.isRadarSimulated.value)
        viewModel.toggleRadarSimulation()
        assertTrue(viewModel.isRadarSimulated.value)
        viewModel.toggleRadarSimulation()
        assertFalse(viewModel.isRadarSimulated.value)
    }

    @Test
    fun dismissPermissionDialog_hidesDialog() = runTest {
        viewModel.dismissPermissionDialog()
        assertFalse(viewModel.showPermissionDialog.value)
    }

    private class FakeBleRepository : BleRepository {
        private val _isSimulated = MutableStateFlow(false)
        override val isRadarSimulationEnabled: StateFlow<Boolean> = _isSimulated

        override fun setRadarSimulationEnabled(enabled: Boolean) {
            _isSimulated.value = enabled
        }

        override fun startScan(): Flow<List<DiscoveredDevice>> = MutableStateFlow(emptyList())
        override fun stopScan() {}
        override fun connectSlot(slot: SensorSlot) {}
        override fun disconnectSlot(type: SensorType) {}

        override val powerDataStream = MutableStateFlow<PowerData?>(null)
        override val heartRateDataStream = MutableStateFlow<HeartRateData?>(null)
        override val radarDataStream = MutableStateFlow<RadarData?>(null)
        override val connectionStateStream: StateFlow<Map<SensorType, ConnectionState>> = MutableStateFlow(
            mapOf(
                SensorType.POWER to ConnectionState.CONNECTED,
                SensorType.HEART_RATE to ConnectionState.CONNECTED,
                SensorType.RADAR to ConnectionState.CONNECTED
            )
        )
    }
}
