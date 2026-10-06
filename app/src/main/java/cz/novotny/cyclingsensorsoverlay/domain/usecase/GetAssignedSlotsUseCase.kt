package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository
import kotlinx.coroutines.flow.Flow

/**
 * Retrieves reactive updates of all saved sensor slot configurations.
 */
class GetAssignedSlotsUseCase(
    private val slotRepository: SensorSlotRepository
) {
    /**
     * Emits a map of current slot assignments keyed by [SensorType].
     */
    operator fun invoke(): Flow<Map<SensorType, SensorSlot>> {
        return slotRepository.getAssignedSlots()
    }
}
