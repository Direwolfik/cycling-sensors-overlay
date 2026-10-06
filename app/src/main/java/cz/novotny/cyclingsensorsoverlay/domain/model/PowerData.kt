package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Telemetry data received from a Cycling Power meter.
 *
 * @property instantaneousPower Current power output in watts.
 * @property cadence Optional pedaling cadence in revolutions per minute (RPM).
 * @property timestamp System time in milliseconds when the sample was parsed.
 */
data class PowerData(
    val instantaneousPower: Int,
    val cadence: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
