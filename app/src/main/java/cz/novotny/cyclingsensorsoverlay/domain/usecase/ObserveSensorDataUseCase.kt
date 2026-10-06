package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.domain.model.*
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines independent streams for power, 3-second power average, heart rate, radar threats,
 * and connection states into a single unified [CombinedSensorState] Flow.
 */
class ObserveSensorDataUseCase(
    private val repository: BleRepository,
    private val calculate3sPowerUseCase: Calculate3sPowerUseCase = Calculate3sPowerUseCase()
) {
    /**
     * Observes and merges all live sensor feeds.
     *
     * @return Hot Flow emitting updated [CombinedSensorState] whenever any sensor value or connection state changes.
     */
    operator fun invoke(): Flow<CombinedSensorState> {
        val power3sFlow = calculate3sPowerUseCase(repository.powerDataStream)

        return combine(
            repository.powerDataStream,
            power3sFlow,
            repository.heartRateDataStream,
            repository.radarDataStream,
            repository.connectionStateStream
        ) { power, p3s, hr, radar, connStates ->
            CombinedSensorState(
                powerData = power,
                power3sAverage = p3s,
                heartRateData = hr,
                radarData = radar,
                connectionStates = connStates
            )
        }
    }
}
