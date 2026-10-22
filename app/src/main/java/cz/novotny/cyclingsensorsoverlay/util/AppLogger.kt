package cz.novotny.cyclingsensorsoverlay.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import cz.novotny.cyclingsensorsoverlay.BuildConfig
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

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
    private val recentLogs = ConcurrentLinkedDeque<String>()
    private const val MAX_LOG_HISTORY = 100

    private fun recordMemoryLog(level: String, tag: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        recentLogs.add("$time [$level/$tag] $message")
        while (recentLogs.size > MAX_LOG_HISTORY) {
            recentLogs.poll()
        }
    }

    /**
     * Returns the formatted recent in-memory log buffer (up to 100 lines).
     */
    fun getRecentLogsText(): String {
        return recentLogs.joinToString("\n")
    }

    /**
     * Initializes Timber trees and Firebase Analytics with the application context.
     */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CrashlyticsTree())

        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.sendUnsentReports()
        }.onFailure {
            Log.e("AppLogger", "Failed to enable Crashlytics collection", it)
        }

        runCatching {
            analytics = FirebaseAnalytics.getInstance(context)
            analytics?.setAnalyticsCollectionEnabled(true)
        }.onFailure {
            Log.e("AppLogger", "Failed to enable Analytics collection", it)
        }
    }

    /**
     * Logs a debug message using Timber and memory buffer.
     */
    fun d(tag: String, message: String) {
        recordMemoryLog("D", tag, message)
        Timber.tag(tag).d(message)
    }

    /**
     * Logs an info message using Timber and memory buffer.
     */
    fun i(tag: String, message: String) {
        recordMemoryLog("I", tag, message)
        Timber.tag(tag).i(message)
    }

    /**
     * Logs a warning message using Timber and sets a custom state key in Crashlytics.
     */
    fun w(tag: String, message: String) {
        recordMemoryLog("W", tag, message)
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
        recordMemoryLog("E", tag, message)
        val nonFatal = throwable ?: Exception("[$tag] $message")
        Timber.tag(tag).e(nonFatal, message)
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

    /**
     * Manually triggers a Crashlytics non-fatal exception snapshot containing current session keys
     * and log history, flushing unsent reports to ensure delivery, and copying logs to clipboard.
     */
    fun sendManualDebugSnapshot(context: Context, extraInfo: String = "") {
        val timestamp = System.currentTimeMillis()
        Log.i("ManualUserDebug", "sendManualDebugSnapshot triggered. Extra: $extraInfo")

        setCustomKey("manual_debug_trigger_time", timestamp.toString())
        if (extraInfo.isNotBlank()) {
            setCustomKey("manual_debug_info", extraInfo)
        }

        val nonFatal = Exception("[ManualUserDebug] Ride debug snapshot uploaded at $timestamp. Details: $extraInfo")
        
        var uploadSuccess = false
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.recordException(nonFatal)
            crashlytics.sendUnsentReports()
            uploadSuccess = true
            Log.i("ManualUserDebug", "Successfully recorded exception and called sendUnsentReports in FirebaseCrashlytics")
        }.onFailure {
            Log.e("ManualUserDebug", "Failed to recordException in FirebaseCrashlytics", it)
        }

        Timber.tag("ManualUserDebug").e(nonFatal, "User pressed 'Send Debug Logs' button during ride")
        setCustomKey("last_error", "[ManualUserDebug] User triggered manual debug snapshot")

        logEvent("user_triggered_debug_snapshot") {
            putString("extra_info", extraInfo)
            putLong("timestamp", timestamp)
        }

        // Copy snapshot and recent logs directly to Android system clipboard as a reliable fallback
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText(
                "Cycling Sensors Debug Logs",
                "Snapshot: $extraInfo\n\nRecent In-Memory Logs:\n${getRecentLogsText()}"
            )
            clipboard?.setPrimaryClip(clipData)
        }

        val toastText = if (uploadSuccess) {
            "Debug logs uploaded to Firebase Crashlytics & copied to clipboard!"
        } else {
            "Crashlytics queued; logs copied to clipboard."
        }

        runCatching {
            android.widget.Toast.makeText(
                context,
                toastText,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Opens Android system sharesheet allowing rider to share recent logs directly via email, notes, or chat.
     */
    fun shareDebugLogs(context: Context, extraInfo: String = "") {
        runCatching {
            val fullText = "Cycling Sensors Overlay Debug Logs\n$extraInfo\n\nRecent Logs:\n${getRecentLogsText()}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Cycling Sensors Overlay Debug Logs")
                putExtra(Intent.EXTRA_TEXT, fullText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Debug Logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
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
