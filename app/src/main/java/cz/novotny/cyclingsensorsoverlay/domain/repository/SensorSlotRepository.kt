package cz.novotny.cyclingsensorsoverlay.domain.repository

import cz.novotny.cyclingsensorsoverlay.domain.model.SensorSlot
import cz.novotny.cyclingsensorsoverlay.domain.model.SensorType
import kotlinx.coroutines.flow.Flow

/**
 * Interface for persisting and retrieving assigned sensor slot configurations.
 */
interface SensorSlotRepository {
    /** Emits a map of current sensor slot assignments keyed by [SensorType]. */
    fun getAssignedSlots(): Flow<Map<SensorType, SensorSlot>>

    /** Persists updated [slot] metadata and MAC address assignment. */
    suspend fun saveSlot(slot: SensorSlot)

    /** Clears saved paired device info for the specified [sensorType]. */
    suspend fun clearSlot(sensorType: SensorType)

    /** Clears all paired sensor slot configurations. */
    suspend fun clearAllSlots()
}
