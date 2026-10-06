package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Represents the unified real-time telemetry state collected from all connected cycling sensors.
 *
 * @property powerData Current instantaneous power and cadence data, or null if unavailable.
 * @property power3sAverage Calculated 3-second moving average power, or null if insufficient data.
 * @property heartRateData Current heart rate data in BPM, or null if unavailable.
 * @property radarData Live rear-radar vehicle threat notifications, or null if unavailable.
 * @property connectionStates Map tracking individual sensor connection statuses across supported slots.
 */
data class CombinedSensorState(
    val powerData: PowerData? = null,
    val power3sAverage: Int? = null,
    val heartRateData: HeartRateData? = null,
    val radarData: RadarData? = null,
    val connectionStates: Map<SensorType, ConnectionState> = mapOf(
        SensorType.POWER to ConnectionState.DISCONNECTED,
        SensorType.HEART_RATE to ConnectionState.DISCONNECTED,
        SensorType.RADAR to ConnectionState.DISCONNECTED
    )
)
