package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository

/**
 * Initiates GATT connection to a sensor using its saved slot configuration.
 */
class ConnectSensorUseCase(private val repository: BleRepository) {
    /**
     * Triggers connection to the specified [slot].
     */
    operator fun invoke(slot: SensorSlot) {
        repository.connectSlot(slot)
    }
}
