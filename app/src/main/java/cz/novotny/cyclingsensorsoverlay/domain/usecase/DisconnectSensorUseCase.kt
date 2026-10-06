package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository

/**
 * Disconnects the GATT connection for a specified sensor type.
 */
class DisconnectSensorUseCase(private val repository: BleRepository) {
    /**
     * Disconnects the active sensor connection for [type].
     */
    operator fun invoke(type: SensorType) {
        repository.disconnectSlot(type)
    }
}
