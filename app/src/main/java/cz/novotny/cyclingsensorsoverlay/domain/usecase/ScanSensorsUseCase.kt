package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.DiscoveredDevice
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow

/**
 * Manages start and stop triggers for BLE sensor discovery scanning.
 */
class ScanSensorsUseCase(private val repository: BleRepository) {
    /** Starts or subscribes to the BLE scanner Flow. */
    fun startScan(): Flow<List<DiscoveredDevice>> = repository.startScan()

    /** Stops active BLE scan. */
    fun stopScan() = repository.stopScan()
}
