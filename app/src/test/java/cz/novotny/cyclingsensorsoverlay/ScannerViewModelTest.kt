package cz.novotny.cyclingsensorsoverlay

import cz.novotny.cyclingsensorsoverlay.domain.model.ConnectionState
import cz.novotny.cyclingsensorsoverlay.domain.model.DiscoveredDevice
import cz.novotny.cyclingsensorsoverlay.domain.model.HeartRateData
import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository
import cz.novotny.cyclingsensorsoverlay.domain.usecase.AssignSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ClearSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ConnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.DisconnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.GetAssignedSlotsUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ScanSensorsUseCase
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel
import cz.novotny.cyclingsensorsoverlay.util.PermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeBleRepository: FakeBleRepository
    private lateinit var fakeSlotRepository: FakeSensorSlotRepository
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBleRepository = FakeBleRepository()
        fakeSlotRepository = FakeSensorSlotRepository()

        viewModel = ScannerViewModel(
            scanSensorsUseCase = ScanSensorsUseCase(fakeBleRepository),
            connectSensorUseCase = ConnectSensorUseCase(fakeBleRepository),
            disconnectSensorUseCase = DisconnectSensorUseCase(fakeBleRepository),
            getAssignedSlotsUseCase = GetAssignedSlotsUseCase(fakeSlotRepository),
            assignSlotUseCase = AssignSlotUseCase(fakeSlotRepository, fakeBleRepository),
            clearSlotUseCase = ClearSlotUseCase(fakeSlotRepository, fakeBleRepository),
            bleRepository = fakeBleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun permissionsUpdate_updatesUiStateAndTriggersAutoConnectForAssignedSlots() = runTest {
        val slotWithMac = SensorSlot("power_slot", SensorType.POWER, "Stages Power", "11:22:33:44:55:66")
        fakeSlotRepository.saveSlot(slotWithMac)
        advanceUntilIdle()

        val permissions = PermissionsState(hasBluetoothPermission = true, hasOverlayPermission = true)
        viewModel.updatePermissionsState(permissions)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.permissionsState.hasBluetoothPermission)
        assertTrue(state.permissionsState.hasOverlayPermission)
        assertEquals(ConnectionState.CONNECTED, state.connectionStates[SensorType.POWER])
    }

    @Test
    fun scanToggle_startsAndStopsScanWhenPermissionGranted() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        advanceUntilIdle()

        viewModel.toggleScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScanning)

        fakeBleRepository.emitDiscoveredDevices(
            listOf(
                DiscoveredDevice("Garmin Varia", "AA:BB:CC:DD:EE:FF", -60, SensorType.RADAR)
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discoveredDevices.size)
        assertEquals("Garmin Varia", viewModel.uiState.value.discoveredDevices.first().name)

        viewModel.toggleScan()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    fun assignDeviceToSlot_savesSlotAndConnects() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        advanceUntilIdle()

        val device = DiscoveredDevice("Tickr HR", "99:88:77:66:55:44", -70, SensorType.HEART_RATE)
        viewModel.assignDeviceToSlot(device, SensorType.HEART_RATE)
        advanceUntilIdle()

        val hrSlot = viewModel.uiState.value.assignedSlots[SensorType.HEART_RATE]
        assertEquals("Tickr HR", hrSlot?.name)
        assertEquals("99:88:77:66:55:44", hrSlot?.macAddress)
        assertEquals(ConnectionState.CONNECTED, viewModel.uiState.value.connectionStates[SensorType.HEART_RATE])
    }

    @Test
    fun clearSlotAssignment_disconnectsAndClearsSlot() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        val device = DiscoveredDevice("Tickr HR", "99:88:77:66:55:44", -70, SensorType.HEART_RATE)
        viewModel.assignDeviceToSlot(device, SensorType.HEART_RATE)
        advanceUntilIdle()

        viewModel.clearSlotAssignment(SensorType.HEART_RATE)
        advanceUntilIdle()

        val hrSlot = viewModel.uiState.value.assignedSlots[SensorType.HEART_RATE]
        assertTrue(hrSlot?.macAddress.isNullOrBlank())
        assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionStates[SensorType.HEART_RATE])
    }

    @Test
    fun discoveredDevice_matchingLogic_detectsPowerHeartRateRadarCorrectly() {
        val powerDeviceByUuid = DiscoveredDevice("Generic", "11:11", -60, serviceUuids = listOf("00001818-0000-1000-8000-00805f9b34fb"))
        val powerDeviceByName = DiscoveredDevice("Favero Assioma Duo", "11:22", -60)
        val hrDeviceByName = DiscoveredDevice("Wahoo TICKR", "33:44", -65)
        val radarDeviceByName = DiscoveredDevice("Garmin Varia RTL515", "55:66", -70)

        assertTrue(powerDeviceByUuid.isPowerMatch())
        assertTrue(powerDeviceByName.isPowerMatch())
        assertTrue(hrDeviceByName.isHeartRateMatch())
        assertTrue(radarDeviceByName.isRadarMatch())
    }

    @Test
    fun smartSorting_prioritizesTargetSlotMatchesAndStrongerRssi() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        viewModel.startScan()
        advanceUntilIdle()

        val dev1 = DiscoveredDevice("Generic Smartwatch", "11:11:11:11:11:11", -45)
        val dev2 = DiscoveredDevice("Stages Power", "22:22:22:22:22:22", -70, serviceUuids = listOf("00001818-0000-1000-8000-00805f9b34fb"))
        val dev3 = DiscoveredDevice("Favero Assioma", "33:33:33:33:33:33", -80)

        fakeBleRepository.emitDiscoveredDevices(listOf(dev1, dev2, dev3))
        advanceUntilIdle()

        viewModel.setTargetSlot(SensorType.POWER)
        advanceUntilIdle()

        val sorted = viewModel.uiState.value.discoveredDevices
        assertEquals(3, sorted.size)
        // Stages Power (-70 dBm) and Favero Assioma (-80 dBm) match POWER -> come first!
        assertEquals("Stages Power", sorted[0].name)
        assertEquals("Favero Assioma", sorted[1].name)
        assertEquals("Generic Smartwatch", sorted[2].name)
    }

    @Test
    fun noiseFiltering_filtersWeakUnnamedDevicesByDefault() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        viewModel.startScan()
        advanceUntilIdle()

        val validDev = DiscoveredDevice("Stages Power", "11:11", -70)
        val weakNoise = DiscoveredDevice(null, "22:22", -95)

        fakeBleRepository.emitDiscoveredDevices(listOf(validDev, weakNoise))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discoveredDevices.size)
        assertEquals("Stages Power", viewModel.uiState.value.discoveredDevices.first().name)

        viewModel.setShowAllDevices(true)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.discoveredDevices.size)
    }

    @Test
    fun openPairingSheet_setsActivePairingSheetSlotAndStartsScan() = runTest {
        viewModel.updatePermissionsState(PermissionsState(hasBluetoothPermission = true))
        advanceUntilIdle()

        viewModel.openPairingSheet(SensorType.POWER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.POWER, state.activePairingSheetSlot)
        assertEquals(SensorType.POWER, state.targetSlot)
        assertTrue(state.isScanning)

        viewModel.closePairingSheet()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.activePairingSheetSlot)
    }

    private class FakeBleRepository : BleRepository {
        private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        private val _connectionStates = MutableStateFlow<Map<SensorType, ConnectionState>>(
            mapOf(
                SensorType.POWER to ConnectionState.DISCONNECTED,
                SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
                SensorType.RADAR to ConnectionState.DISCONNECTED
            )
        )

        override fun startScan(): Flow<List<DiscoveredDevice>> = _discoveredDevices
        override fun stopScan() {}
        override fun connectSlot(slot: SensorSlot) {
            val updated = _connectionStates.value.toMutableMap()
            updated[slot.sensorType] = ConnectionState.CONNECTED
            _connectionStates.value = updated
        }
        override fun disconnectSlot(type: SensorType) {
            val updated = _connectionStates.value.toMutableMap()
            updated[type] = ConnectionState.DISCONNECTED
            _connectionStates.value = updated
        }

        private val _isSimulated = MutableStateFlow(false)
        override val isRadarSimulationEnabled: StateFlow<Boolean> = _isSimulated
        override fun setRadarSimulationEnabled(enabled: Boolean) {
            _isSimulated.value = enabled
        }

        override val powerDataStream = MutableStateFlow<PowerData?>(null)
        override val heartRateDataStream = MutableStateFlow<HeartRateData?>(null)
        override val radarDataStream = MutableStateFlow<RadarData?>(null)
        override val connectionStateStream: StateFlow<Map<SensorType, ConnectionState>> = _connectionStates

        fun emitDiscoveredDevices(devices: List<DiscoveredDevice>) {
            _discoveredDevices.value = devices
        }
    }

    private class FakeSensorSlotRepository : SensorSlotRepository {
        private val _slots = MutableStateFlow<Map<SensorType, SensorSlot>>(
            mapOf(
                SensorType.POWER to SensorSlot("power_slot", SensorType.POWER),
                SensorType.HEART_RATE to SensorSlot("hr_slot", SensorType.HEART_RATE),
                SensorType.RADAR to SensorSlot("radar_slot", SensorType.RADAR)
            )
        )

        override fun getAssignedSlots(): Flow<Map<SensorType, SensorSlot>> = _slots

        override suspend fun saveSlot(slot: SensorSlot) {
            val updated = _slots.value.toMutableMap()
            updated[slot.sensorType] = slot
            _slots.value = updated
        }

        override suspend fun clearSlot(sensorType: SensorType) {
            val updated = _slots.value.toMutableMap()
            updated[sensorType] = SensorSlot("${sensorType.name.lowercase()}_slot", sensorType)
            _slots.value = updated
        }

        override suspend fun clearAllSlots() {
            _slots.value = mapOf(
                SensorType.POWER to SensorSlot("power_slot", SensorType.POWER),
                SensorType.HEART_RATE to SensorSlot("hr_slot", SensorType.HEART_RATE),
                SensorType.RADAR to SensorSlot("radar_slot", SensorType.RADAR)
            )
        }
    }
}
