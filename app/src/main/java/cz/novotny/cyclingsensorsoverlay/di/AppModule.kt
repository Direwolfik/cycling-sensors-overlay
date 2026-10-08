package cz.novotny.cyclingsensorsoverlay.di

import cz.novotny.cyclingsensorsoverlay.data.ble.BleManager
import cz.novotny.cyclingsensorsoverlay.data.ble.BleRepositoryImpl
import cz.novotny.cyclingsensorsoverlay.data.location.GpsLocationManager
import cz.novotny.cyclingsensorsoverlay.data.repository.SensorSlotRepositoryImpl
import cz.novotny.cyclingsensorsoverlay.domain.repository.BleRepository
import cz.novotny.cyclingsensorsoverlay.domain.repository.SensorSlotRepository
import cz.novotny.cyclingsensorsoverlay.domain.usecase.AssignSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.Calculate3sPowerUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ClearSlotUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ConnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.DisconnectSensorUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.GetAssignedSlotsUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ScanSensorsUseCase
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Data sources & Repositories
    single { BleManager(androidContext()) }
    single { GpsLocationManager(androidContext()) }
    singleOf(::BleRepositoryImpl) { bind<BleRepository>() }
    single<SensorSlotRepository> { SensorSlotRepositoryImpl(androidContext()) }

    // Domain Use Cases
    factoryOf(::Calculate3sPowerUseCase)
    factoryOf(::ScanSensorsUseCase)
    factoryOf(::ConnectSensorUseCase)
    factoryOf(::DisconnectSensorUseCase)
    factoryOf(::GetAssignedSlotsUseCase)
    factoryOf(::AssignSlotUseCase)
    factoryOf(::ClearSlotUseCase)
    factoryOf(::ObserveSensorDataUseCase)

    // ViewModels
    viewModelOf(::ScannerViewModel)
    viewModelOf(::DashboardViewModel)
}
