package cz.novotny.cyclingsensorsoverlay.domain.model

import java.util.Locale

/**
 * Represents a Bluetooth Low Energy device discovered during scanning.
 *
 * Provides helper functions for heuristic sensor classification based on BLE Service UUIDs
 * and advertised device names.
 *
 * @property name Advertised Bluetooth device name, or null if unnamed.
 * @property address Hardware MAC address of the BLE device.
 * @property rssi Quantized or smoothed Received Signal Strength Indicator in dBm.
 * @property detectedType Sensor type explicitly assigned or auto-inferred.
 * @property serviceUuids List of advertised GATT service UUIDs.
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val detectedType: SensorType? = null,
    val serviceUuids: List<String> = emptyList()
) {
    /**
     * Checks if the device matches a Cycling Power sensor via GATT UUID 0x1818 or known manufacturer keywords.
     */
    fun isPowerMatch(): Boolean {
        if (serviceUuids.any { uuidMatches(it, "1818") }) return true
        val lowerName = name?.lowercase(Locale.ROOT) ?: return false
        val keywords = listOf("assioma", "favero", "pedal", "power", "stages", "4iiii", "quarq", "rally", "watt")
        return keywords.any { lowerName.contains(it) }
    }

    /**
     * Checks if the device matches a Heart Rate monitor via GATT UUID 0x180D or common name keywords.
     */
    fun isHeartRateMatch(): Boolean {
        if (serviceUuids.any { uuidMatches(it, "180d") }) return true
        val lowerName = name?.lowercase(Locale.ROOT) ?: return false
        val keywords = listOf("hrm", "heart", "garmin", "polar", "wahoo", "tickr", "coospo", "watch", "chest", "band", "hr")
        return keywords.any { lowerName.contains(it) }
    }

    /**
     * Checks if the device matches a Cycling Radar sensor via standard UUID 0x183C, custom Varia UUID 0x6E40, or keywords.
     */
    fun isRadarMatch(): Boolean {
        if (serviceUuids.any { uuidMatches(it, "183c") || uuidMatches(it, "6e400001") }) return true
        val lowerName = name?.lowercase(Locale.ROOT) ?: return false
        val keywords = listOf("tr70", "radar", "varia", "coospo", "rtl", "magene", "l508", "gardia", "bryton")
        return keywords.any { lowerName.contains(it) }
    }

    /**
     * Evaluates whether this device matches the specified [sensorType] or any known cycling sensor type if null.
     */
    fun isMatchFor(sensorType: SensorType?): Boolean {
        return when (sensorType) {
            SensorType.POWER -> isPowerMatch()
            SensorType.HEART_RATE -> isHeartRateMatch()
            SensorType.RADAR -> isRadarMatch()
            null -> isPowerMatch() || isHeartRateMatch() || isRadarMatch()
        }
    }

    /**
     * Inferred sensor type based on explicit detection or heuristic matching rules.
     */
    val inferredType: SensorType?
        get() = when {
            detectedType != null -> detectedType
            isPowerMatch() -> SensorType.POWER
            isHeartRateMatch() -> SensorType.HEART_RATE
            isRadarMatch() -> SensorType.RADAR
            else -> null
        }

    private fun uuidMatches(uuidStr: String, shortHexOrPrefix: String): Boolean {
        val s = uuidStr.lowercase(Locale.ROOT)
        val target = shortHexOrPrefix.lowercase(Locale.ROOT)
        return s == target || s.startsWith("0000$target") || s.startsWith(target) || s.contains(target)
    }
}
