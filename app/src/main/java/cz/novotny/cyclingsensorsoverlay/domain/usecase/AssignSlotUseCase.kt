package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository

/**
 * Persists a sensor slot assignment and optionally initiates immediate BLE connection.
 */
class AssignSlotUseCase(
    private val slotRepository: SensorSlotRepository,
    private val bleRepository: BleRepository
) {
    /**
     * Saves [slot] configuration to storage and attempts connection if [autoConnect] is true.
     */
    suspend operator fun invoke(slot: SensorSlot, autoConnect: Boolean = true) {
        slotRepository.saveSlot(slot)
        if (autoConnect && !slot.macAddress.isNullOrBlank()) {
            bleRepository.connectSlot(slot)
        }
    }
}
