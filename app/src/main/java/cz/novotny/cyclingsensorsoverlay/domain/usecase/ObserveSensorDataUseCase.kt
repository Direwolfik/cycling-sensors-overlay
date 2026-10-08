package cz.novotny.cyclingsensorsoverlay.domain.usecase

import cz.novotny.cyclingsensorsoverlay.data.location.GpsLocationManager
import cz.novotny.cyclingsensorsoverlay.domain.model.*
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Combines independent streams for power, 3-second power average, heart rate, radar threats,
 * GPS speed, and connection states into a single unified [CombinedSensorState] Flow.
 */
class ObserveSensorDataUseCase(
    private val repository: BleRepository,
    private val gpsLocationManager: GpsLocationManager? = null,
    private val calculate3sPowerUseCase: Calculate3sPowerUseCase = Calculate3sPowerUseCase()
) {
    /**
     * Observes and merges all live sensor feeds.
     *
     * @return Hot Flow emitting updated [CombinedSensorState] whenever any sensor value or connection state changes.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(): Flow<CombinedSensorState> {
        val power3sFlow = calculate3sPowerUseCase(repository.powerDataStream)
        val gpsStream = gpsLocationManager?.gpsSpeedStream ?: MutableStateFlow<Float?>(null)

        return combine(
            repository.powerDataStream,
            power3sFlow,
            repository.heartRateDataStream,
            repository.radarDataStream,
            gpsStream,
            repository.connectionStateStream
        ) { array ->
            CombinedSensorState(
                powerData = array[0] as PowerData?,
                power3sAverage = array[1] as Int?,
                heartRateData = array[2] as HeartRateData?,
                radarData = array[3] as RadarData?,
                gpsSpeedKmh = array[4] as Float?,
                connectionStates = array[5] as Map<SensorType, ConnectionState>
            )
        }
    }
}
