# Preserve Kotlin attributes and metadata for R8
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Preserve Firebase Crashlytics & Analytics
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Preserve Timber logging & AppLogger
-keep class com.jakewharton.timber.** { *; }
-keep class cz.novotny.cyclingsensorsoverlay.util.** { *; }

# Preserve App Models, Repositories, and BLE data classes
-keep class cz.novotny.cyclingsensorsoverlay.domain.model.** { *; }
-keep class cz.novotny.cyclingsensorsoverlay.data.** { *; }
-keep class cz.novotny.cyclingsensorsoverlay.service.** { *; }

# Preserve Coroutines & Koin
-keep class kotlinx.coroutines.** { *; }
-keep class org.koin.** { *; }
