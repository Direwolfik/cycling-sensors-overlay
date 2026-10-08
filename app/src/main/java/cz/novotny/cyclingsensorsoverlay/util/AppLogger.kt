package cz.novotny.cyclingsensorsoverlay.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import cz.novotny.cyclingsensorsoverlay.BuildConfig
import timber.log.Timber

/**
 * Unified logger for Cycling Sensors Overlay powered by Timber and Firebase
 * (Firebase Crashlytics & Firebase Analytics).
 *
 * Specifically tailored to assist in remote debugging of Bluetooth Low Energy (BLE) and
 * rear radar stack interactions (e.g., service discovery failures, GATT errors, packet parsing issues,
 * watchdog timeouts, and reconnect cycles).
 */
object AppLogger {

    private var analytics: FirebaseAnalytics? = null

    /**
     * Initializes Timber trees and Firebase Analytics with the application context.
     */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CrashlyticsTree())

        runCatching {
            analytics = FirebaseAnalytics.getInstance(context)
        }
    }

    /**
     * Logs a debug message using Timber.
     */
    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    /**
     * Logs an info message using Timber.
     */
    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    /**
     * Logs a warning message using Timber and sets a custom state key in Crashlytics.
     */
    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
        setCustomKey("last_warning", "[$tag] $message")
    }

    /**
     * Logs an error message using Timber, recording a non-fatal exception in Crashlytics.
     *
     * @param tag Logging tag (e.g., "BleManager", "RadarParser").
     * @param message Diagnostic detail message.
     * @param throwable Optional exception associated with the failure.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.tag(tag).e(message)
        }
        setCustomKey("last_error", "[$tag] $message")
    }

    /**
     * Sets a custom key-value string attribute in Firebase Crashlytics for active session context.
     */
    fun setCustomKey(key: String, value: String) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }
    }

    /**
     * Sets a custom key-value integer attribute in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Int) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }
    }

    /**
     * Sets a custom key-value boolean attribute in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        }
    }

    /**
     * Emits a structured analytics event to Firebase Analytics.
     */
    fun logEvent(eventName: String, params: Bundle.() -> Unit = {}) {
        runCatching {
            val bundle = Bundle().apply(params)
            analytics?.logEvent(eventName, bundle)
        }
    }
}

/**
 * Custom Timber Tree forwarding logs and recorded exceptions to Firebase Crashlytics.
 */
private class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val tagPrefix = if (tag != null) "[$tag] " else ""
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("$tagPrefix$message")
            if (t != null && priority >= Log.ERROR) {
                crashlytics.recordException(t)
            }
        }
    }
}
