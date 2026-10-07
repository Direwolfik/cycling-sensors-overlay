package cz.novotny.cyclingsensorsoverlay.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.model.CombinedSensorState
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.service.OverlayService
import cz.novotny.cyclingsensorsoverlay.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the main dashboard screen.
 *
 * Observes combined real-time sensor streams (power, 3-second moving power average, heart rate, and radar threats),
 * controls radar simulation mode, and manages system overlay foreground service toggling with permission handling.
 */
class DashboardViewModel(
    observeSensorDataUseCase: ObserveSensorDataUseCase,
    private val bleRepository: BleRepository
) : ViewModel() {

    /** StateFlow emitting real-time combined telemetry data and sensor slot connection states. */
    val sensorState: StateFlow<CombinedSensorState> = observeSensorDataUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CombinedSensorState()
        )

    /** StateFlow indicating whether the system floating overlay service is currently running. */
    val isOverlayRunning: StateFlow<Boolean> = OverlayService.isRunning

    /** StateFlow indicating whether rear radar simulation mode is active. */
    val isRadarSimulated: StateFlow<Boolean> = bleRepository.isRadarSimulationEnabled

    private val _showPermissionDialog = MutableStateFlow(false)

    /** StateFlow controlling visibility of the system overlay permission request dialog. */
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _showBluetoothPermissionDialog = MutableStateFlow(false)

    /** StateFlow controlling visibility of the Bluetooth permission request dialog. */
    val showBluetoothPermissionDialog: StateFlow<Boolean> = _showBluetoothPermissionDialog.asStateFlow()

    /** Toggles rear radar telemetry simulation mode. */
    fun toggleRadarSimulation() {
        val current = isRadarSimulated.value
        bleRepository.setRadarSimulationEnabled(!current)
    }

    /**
     * Toggles the floating system overlay service.
     * Checks for system alert window permission (`SYSTEM_ALERT_WINDOW`) and Bluetooth permissions;
     * if both are granted, starts or stops [OverlayService], otherwise prompts the appropriate permission dialog.
     *
     * @param context Application context used for checking permissions and starting/stopping service.
     */
    fun toggleOverlay(context: Context) {
        if (isOverlayRunning.value) {
            OverlayService.stopService(context)
        } else {
            val hasOverlay = PermissionUtils.hasOverlayPermission(context)
            val hasBluetooth = PermissionUtils.hasBluetoothPermission(context)
            if (!hasOverlay) {
                _showPermissionDialog.value = true
            } else if (!hasBluetooth) {
                _showBluetoothPermissionDialog.value = true
            } else {
                OverlayService.startService(context)
            }
        }
    }

    /** Dismisses the overlay permission dialog. */
    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    /** Dismisses the bluetooth permission dialog. */
    fun dismissBluetoothPermissionDialog() {
        _showBluetoothPermissionDialog.value = false
    }

    /** Opens system settings page for granting `SYSTEM_ALERT_WINDOW` permission. */
    fun openOverlaySettings(context: Context) {
        PermissionUtils.openOverlaySettings(context)
        _showPermissionDialog.value = false
    }
}
