package cz.novotny.cyclingsensorsoverlay

import android.app.Application
import cz.novotny.cyclingsensorsoverlay.di.appModule
import cz.novotny.cyclingsensorsoverlay.util.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CyclingOverlayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("Application", "Cycling Overlay Application initialized with Firebase logging enabled")

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@CyclingOverlayApplication)
            modules(appModule)
        }
    }
}
