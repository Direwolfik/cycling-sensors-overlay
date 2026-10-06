package cz.novotny.cyclingsensorsoverlay.data.ble

import cz.novotny.cyclingsensorsoverlay.domain.model.*
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementation of [BleRepository] delegating hardware GATT operations, scanner callbacks,
 * and state management to [BleManager].
 */
class BleRepositoryImpl(
    private val bleManager: BleManager
) : BleRepository {

    override fun startScan(): Flow<List<DiscoveredDevice>> {
        return bleManager.startScan()
    }

    override fun stopScan() {
        bleManager.stopScan()
    }

    override fun connectSlot(slot: SensorSlot) {
        bleManager.connectSlot(slot)
    }

    override fun disconnectSlot(type: SensorType) {
        bleManager.disconnectSlot(type)
    }

    override fun setRadarSimulationEnabled(enabled: Boolean) {
        bleManager.setRadarSimulationEnabled(enabled)
    }

    override val isRadarSimulationEnabled: StateFlow<Boolean> = bleManager.isRadarSimulated

    override val powerDataStream: Flow<PowerData?> = bleManager.powerDataStream
    override val heartRateDataStream: Flow<HeartRateData?> = bleManager.heartRateDataStream
    override val radarDataStream: Flow<RadarData?> = bleManager.radarDataStream
    override val connectionStateStream: StateFlow<Map<SensorType, ConnectionState>> = bleManager.connectionStates
}
