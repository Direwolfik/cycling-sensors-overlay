package cz.novotny.cyclingsensorsoverlay.domain.model

/**
 * Threat urgency level detected by cycling radar.
 *
 * @property value Numeric representation of threat severity (0 = Clear/Low, 1 = Medium/Approaching, 2 = High Speed/Fast Approach).
 */
enum class ThreatLevel(val value: Int) {
    NONE(0),
    APPROACHING(1),
    HIGH_SPEED(2);

    companion object {
        fun fromValue(value: Int): ThreatLevel {
            return entries.firstOrNull { it.value == value } ?: if (value > 0) APPROACHING else NONE
        }
    }
}

/**
 * Represents an individual approaching vehicle threat detected by cycling radar.
 *
 * @property id Identifier or tracking index for the target vehicle.
 * @property threatLevel Threat urgency level ([ThreatLevel.NONE], [ThreatLevel.APPROACHING], [ThreatLevel.HIGH_SPEED]).
 * @property distanceMeters Estimated distance from the cyclist to the target vehicle in meters.
 * @property speedKmH Relative approach speed of the target vehicle in km/h.
 */
data class RadarThreat(
    val id: Int = 0,
    val threatLevel: ThreatLevel = ThreatLevel.NONE,
    val distanceMeters: Float,
    val speedKmH: Float
)
