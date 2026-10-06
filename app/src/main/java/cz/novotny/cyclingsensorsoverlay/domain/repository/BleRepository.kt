package cz.novotny.cyclingsensorsoverlay.domain.repository

import cz.novotny.cyclingsensorsoverlay.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining BLE operations for device scanning, GATT slot connection lifecycle,
 * radar simulation, and telemetry stream emissions.
 */
interface BleRepository {
    /** Starts or subscribes to the BLE scanner emitting discovered devices. */
    fun startScan(): Flow<List<DiscoveredDevice>>

    /** Stops any active BLE scan. */
    fun stopScan()

    /** Connects to a sensor using its configured [slot] MAC address. */
    fun connectSlot(slot: SensorSlot)

    /** Disconnects the active sensor GATT connection for [type]. */
    fun disconnectSlot(type: SensorType)

    /** Enables or disables simulated rear radar threat telemetry. */
    fun setRadarSimulationEnabled(enabled: Boolean)

    /** Indicates whether radar simulation is active. */
    val isRadarSimulationEnabled: StateFlow<Boolean>

    /** Hot stream of live cycling power and cadence data. */
    val powerDataStream: Flow<PowerData?>

    /** Hot stream of live heart rate measurements. */
    val heartRateDataStream: Flow<HeartRateData?>

    /** Hot stream of live radar threat snapshots. */
    val radarDataStream: Flow<RadarData?>

    /** Map tracking the current [ConnectionState] for each [SensorType]. */
    val connectionStateStream: StateFlow<Map<SensorType, ConnectionState>>
}
