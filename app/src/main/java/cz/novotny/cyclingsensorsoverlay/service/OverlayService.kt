package cz.novotny.cyclingsensorsoverlay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cz.novotny.cyclingsensorsoverlay.MainActivity
import cz.novotny.cyclingsensorsoverlay.domain.model.CombinedSensorState
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.ui.overlay.OverlayWidget
import cz.novotny.cyclingsensorsoverlay.ui.overlay.RadarSideBarWidget
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme
import cz.novotny.cyclingsensorsoverlay.util.OverlayLifecycleOwner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Foreground Service managing system-wide floating overlay windows (`TYPE_APPLICATION_OVERLAY`).
 *
 * Renders real-time cycling telemetry widgets and vertical radar threat sidebars on top of other applications.
 *
 * Key Capabilities:
 * - Foreground Service Lifecycle: Runs with ongoing notification channel and `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on Android 14+.
 * - Custom Lifecycle Scope: Manages an [OverlayLifecycleOwner] attached to root `ComposeView`s for lifecycle and SavedState handling outside standard Activity contexts.
 * - WindowManager Layout Setup: Configures non-focusable floating window parameters (`FLAG_NOT_FOCUSABLE` | `FLAG_LAYOUT_IN_SCREEN`), persisting user drag positions to `SharedPreferences`.
 * - Dual Jetpack Compose Overlays: Renders [OverlayWidget] (power, 3s power average, HR, cadence) and [RadarSideBarWidget] (rear radar vehicle approach sidebar) concurrently.
 */
class OverlayService : Service(), KoinComponent {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val PREFS_NAME = "overlay_prefs"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"
        private const val KEY_RADAR_POS_X = "radar_pos_x"
        private const val KEY_RADAR_POS_Y = "radar_pos_y"

        private val _isRunning = MutableStateFlow(false)

        /** StateFlow indicating whether [OverlayService] is actively running. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Starts [OverlayService] in the foreground. */
        fun startService(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        /** Stops [OverlayService]. */
        fun stopService(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }

    private val observeSensorDataUseCase: ObserveSensorDataUseCase by inject()

    private lateinit var windowManager: WindowManager
    private var telemetryComposeView: ComposeView? = null
    private var radarComposeView: ComposeView? = null
    private lateinit var telemetryWindowParams: WindowManager.LayoutParams
    private lateinit var radarWindowParams: WindowManager.LayoutParams

    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val sensorState = MutableStateFlow(CombinedSensorState())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true

        overlayLifecycleOwner.onCreate()
        overlayLifecycleOwner.onStart()
        overlayLifecycleOwner.onResume()

        serviceScope.launch {
            observeSensorDataUseCase().collect { state ->
                sensorState.value = state
            }
        }

        startForegroundNotification()
        setupOverlayViews()
    }

    /**
     * Creates notification channel and promotes service to foreground with persistent notification.
     */
    private fun startForegroundNotification() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cycling Sensors Overlay")
            .setContentText("Floating live telemetry & radar overlays active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType
            )
        } catch (e: SecurityException) {
            android.util.Log.e("OverlayService", "SecurityException starting foreground service", e)
            _isRunning.value = false
            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Exception starting foreground service", e)
            _isRunning.value = false
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground service notification for cycling sensors overlay"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /** Initialized WindowManager and attaches telemetry and radar overlay windows. */
    private fun setupOverlayViews() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupTelemetryWidget(prefs)
        setupRadarSideBar(prefs)
    }

    /**
     * Configures WindowManager layout parameters and Compose view content for the telemetry overlay widget.
     * Restores last saved X/Y position from [prefs] and updates window coordinates upon user drag events.
     */
    private fun setupTelemetryWidget(prefs: SharedPreferences) {
        val initialX = prefs.getInt(KEY_POS_X, 100)
        val initialY = prefs.getInt(KEY_POS_Y, 200)

        telemetryWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                CyclingSensorsOverlayTheme {
                    val state by sensorState.collectAsState()
                    OverlayWidget(
                        sensorState = state,
                        onCloseClick = { stopSelf() },
                        onDrag = { dx, dy ->
                            telemetryWindowParams.x += dx
                            telemetryWindowParams.y += dy
                            try {
                                windowManager.updateViewLayout(this, telemetryWindowParams)
                            } catch (_: Exception) {}
                        },
                        onDragEnd = {
                            prefs.edit {
                                putInt(KEY_POS_X, telemetryWindowParams.x)
                                putInt(KEY_POS_Y, telemetryWindowParams.y)
                            }
                        }
                    )
                }
            }
        }

        telemetryComposeView = view
        windowManager.addView(view, telemetryWindowParams)
    }

    /**
     * Configures WindowManager layout parameters and Compose view content for the vertical radar sidebar overlay widget.
     * Restores last saved X/Y position from [prefs] and updates window coordinates upon user drag events.
     */
    private fun setupRadarSideBar(prefs: SharedPreferences) {
        val initialX = prefs.getInt(KEY_RADAR_POS_X, 0)
        val initialY = prefs.getInt(KEY_RADAR_POS_Y, 250)

        radarWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = initialX
            y = initialY
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            setContent {
                CyclingSensorsOverlayTheme {
                    val state by sensorState.collectAsState()
                    RadarSideBarWidget(
                        radarData = state.radarData,
                        onCloseClick = {
                            radarComposeView?.let { view ->
                                try {
                                    windowManager.removeView(view)
                                } catch (_: Exception) {}
                            }
                            radarComposeView = null
                        },
                        onDrag = { dx, dy ->
                            radarWindowParams.x = (radarWindowParams.x - dx).coerceAtLeast(0)
                            radarWindowParams.y += dy
                            try {
                                windowManager.updateViewLayout(this, radarWindowParams)
                            } catch (_: Exception) {}
                        },
                        onDragEnd = {
                            prefs.edit {
                                putInt(KEY_RADAR_POS_X, radarWindowParams.x)
                                putInt(KEY_RADAR_POS_Y, radarWindowParams.y)
                            }
                        }
                    )
                }
            }
        }

        radarComposeView = view
        windowManager.addView(view, radarWindowParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false

        telemetryComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        telemetryComposeView = null

        radarComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        radarComposeView = null

        overlayLifecycleOwner.onPause()
        overlayLifecycleOwner.onStop()
        overlayLifecycleOwner.onDestroy()

        serviceScope.cancel()
    }
}
