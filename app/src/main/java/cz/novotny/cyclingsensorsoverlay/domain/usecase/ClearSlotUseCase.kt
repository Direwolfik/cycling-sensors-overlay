package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository

/**
 * Disconnects the active sensor GATT connection and clears the persisted slot assignment.
 */
class ClearSlotUseCase(
    private val slotRepository: SensorSlotRepository,
    private val bleRepository: BleRepository
) {
    /**
     * Disconnects and unassigns the specified [sensorType].
     */
    suspend operator fun invoke(sensorType: SensorType) {
        bleRepository.disconnectSlot(sensorType)
        slotRepository.clearSlot(sensorType)
    }
}
