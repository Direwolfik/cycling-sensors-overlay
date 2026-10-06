package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Telemetry snapshot emitted by a rear-facing radar sensor.
 *
 * @property threats List of approaching vehicle threats detected by radar.
 * @property timestamp System time in milliseconds when the radar snapshot was parsed.
 */
data class RadarData(
    val threats: List<RadarThreat> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
