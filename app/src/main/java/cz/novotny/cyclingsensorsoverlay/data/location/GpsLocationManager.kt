package cz.novotny.cyclingsensorsoverlay.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import cz.novotny.cyclingsensorsoverlay.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages real-time GPS location updates and calculates current cycling speed in km/h.
 * Uses Google Play Services FusedLocationProviderClient with high-accuracy GPS tracking.
 */
class GpsLocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val _gpsSpeedStream = MutableStateFlow<Float?>(null)

    /** Hot stream emitting calculated GPS speed in km/h, or null when location is unavailable. */
    val gpsSpeedStream: StateFlow<Float?> = _gpsSpeedStream.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var isTracking = false

    /**
     * Starts active high-accuracy GPS location tracking if permissions are granted.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTracking) return
        if (!PermissionUtils.hasLocationPermission(context)) {
            AppLogger.w(TAG, "Cannot start GPS tracking: Location permissions not granted")
            return
        }

        AppLogger.i(TAG, "Starting GPS location speed tracking")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setGranularity(Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(false)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                processLocationUpdate(location)
            }
        }

        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            isTracking = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start GPS location updates", e)
        }
    }

    private fun processLocationUpdate(location: Location) {
        var speedKmh: Float? = null

        if (location.hasSpeed() && location.speed >= 0f) {
            speedKmh = (location.speed * 3.6f).coerceIn(0f, 150f)
        } else {
            val prev = lastLocation
            if (prev != null) {
                val distanceMeters = location.distanceTo(prev)
                val timeSec = (location.time - prev.time) / 1000.0f
                if (timeSec in 0.5f..10f && distanceMeters >= 0f) {
                    val mps = distanceMeters / timeSec
                    speedKmh = (mps * 3.6f).coerceIn(0f, 150f)
                }
            }
        }

        lastLocation = location
        _gpsSpeedStream.value = speedKmh
        AppLogger.d(TAG, "GPS Location speed update: $speedKmh km/h")
    }

    /**
     * Stops GPS location updates and clears tracking state.
     */
    fun stopLocationUpdates() {
        if (!isTracking) return
        AppLogger.i(TAG, "Stopping GPS location tracking")
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing location updates", e)
            }
        }
        locationCallback = null
        isTracking = false
        lastLocation = null
        _gpsSpeedStream.value = null
    }

    companion object {
        private const val TAG = "GpsLocationManager"
    }
}
