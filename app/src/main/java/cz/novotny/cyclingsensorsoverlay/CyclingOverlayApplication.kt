package cz.novotny.cyclingsensorsoverlay

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.novotny.cyclingsensorsoverlay.data.ble.BleManager
import cz.novotny.cyclingsensorsoverlay.data.ble.BleRepositoryImpl
import cz.novotny.cyclingsensorsoverlay.data.repository.SensorSlotRepositoryImpl
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository
import cz.novotny.cyclingsensorsoverlay.domain.usecase.AssignSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ClearSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ConnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.DisconnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.GetAssignedSlotsUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ScanSensorsUseCase
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel

import cz.novotny.cyclingsensorsoverlay.util.AppLogger

class CyclingOverlayApplication : Application() {

    lateinit var bleManager: BleManager
        private set
    lateinit var bleRepository: BleRepository
        private set
    lateinit var slotRepository: SensorSlotRepository
        private set

    lateinit var scanSensorsUseCase: ScanSensorsUseCase
        private set
    lateinit var connectSensorUseCase: ConnectSensorUseCase
        private set
    lateinit var disconnectSensorUseCase: DisconnectSensorUseCase
        private set
    lateinit var getAssignedSlotsUseCase: GetAssignedSlotsUseCase
        private set
    lateinit var assignSlotUseCase: AssignSlotUseCase
        private set
    lateinit var clearSlotUseCase: ClearSlotUseCase
        private set
    lateinit var observeSensorDataUseCase: ObserveSensorDataUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("Application", "Cycling Overlay Application initialized with Firebase logging enabled")

        bleManager = BleManager(this)
        bleRepository = BleRepositoryImpl(bleManager)
        slotRepository = SensorSlotRepositoryImpl(this)

        scanSensorsUseCase = ScanSensorsUseCase(bleRepository)
        connectSensorUseCase = ConnectSensorUseCase(bleRepository)
        disconnectSensorUseCase = DisconnectSensorUseCase(bleRepository)
        getAssignedSlotsUseCase = GetAssignedSlotsUseCase(slotRepository)
        assignSlotUseCase = AssignSlotUseCase(slotRepository, bleRepository)
        clearSlotUseCase = ClearSlotUseCase(slotRepository, bleRepository)
        observeSensorDataUseCase = ObserveSensorDataUseCase(bleRepository)
    }
}

class ScannerViewModelFactory(
    private val app: CyclingOverlayApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            return ScannerViewModel(
                scanSensorsUseCase = app.scanSensorsUseCase,
                connectSensorUseCase = app.connectSensorUseCase,
                disconnectSensorUseCase = app.disconnectSensorUseCase,
                getAssignedSlotsUseCase = app.getAssignedSlotsUseCase,
                assignSlotUseCase = app.assignSlotUseCase,
                clearSlotUseCase = app.clearSlotUseCase,
                bleRepository = app.bleRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}

class DashboardViewModelFactory(
    private val app: CyclingOverlayApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(
                observeSensorDataUseCase = app.observeSensorDataUseCase,
                bleRepository = app.bleRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}
