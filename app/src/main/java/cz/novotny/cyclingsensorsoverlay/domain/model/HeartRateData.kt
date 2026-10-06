package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Telemetry data received from a Heart Rate monitor.
 *
 * @property bpm Current heart rate in beats per minute.
 * @property timestamp System time in milliseconds when the sample was parsed.
 */
data class HeartRateData(
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis()
)
