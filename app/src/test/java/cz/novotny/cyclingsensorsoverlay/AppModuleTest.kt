package cz.novotny.cyclingsensorsoverlay

import android.content.Context
import android.content.ContextWrapper
import cz.novotny.cyclingsensorsoverlay.di.appModule
import cz.novotny.cyclingsensorsoverlay.domain.usecase.Calculate3sPowerUseCase
import cz.novotny.cyclingsensorsoverlay.domain.usecase.ObserveSensorDataUseCase
import cz.novotny.cyclingsensorsoverlay.ui.dashboard.DashboardViewModel
import cz.novotny.cyclingsensorsoverlay.ui.scanner.ScannerViewModel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level

class AppModuleTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun verifyKoinModuleDependenciesCanBeResolved() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir(): java.io.File = java.io.File(System.getProperty("java.io.tmpdir") ?: ".")
        }

        val koinApp = startKoin {
            printLogger(Level.ERROR)
            androidContext(dummyContext)
            modules(appModule)
        }

        val koin = koinApp.koin

        val calculate3sUseCase = koin.getOrNull<Calculate3sPowerUseCase>()
        assertNotNull(calculate3sUseCase)

        val observeUseCase = koin.getOrNull<ObserveSensorDataUseCase>()
        assertNotNull(observeUseCase)

        val dashboardVm = koin.getOrNull<DashboardViewModel>()
        assertNotNull(dashboardVm)

        val scannerVm = koin.getOrNull<ScannerViewModel>()
        assertNotNull(scannerVm)
    }
}
