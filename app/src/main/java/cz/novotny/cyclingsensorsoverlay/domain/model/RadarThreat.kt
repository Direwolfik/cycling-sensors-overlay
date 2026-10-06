package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Represents an individual approaching vehicle threat detected by cycling radar.
 *
 * @property id Identifier or tracking index for the target vehicle.
 * @property threatLevel Threat urgency level (0 = Clear/Low, 1 = Medium/Approaching, 2 = High Speed/Fast Approach).
 * @property distanceMeters Estimated distance from the cyclist to the target vehicle in meters.
 * @property speedKmH Relative approach speed of the target vehicle in km/h.
 */
data class RadarThreat(
    val id: Int = 0,
    val threatLevel: Int,
    val distanceMeters: Float,
    val speedKmH: Float
)
