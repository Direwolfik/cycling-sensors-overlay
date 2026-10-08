# Cycling Sensors Overlay

An Android application that connects via Bluetooth Low Energy (BLE) to cycling sensors (Power Meters, Heart Rate Monitors, Rear Radars) and displays real-time telemetry metrics both in an in-app Live Dashboard and as a system-wide floating overlay widget on top of other apps.

---

## 🚴 Features

- **Live In-App Dashboard**: Displays real-time metrics including power (Watts), 3-second average power, cadence (RPM), heart rate (BPM), GPS speed (km/h), and rear radar vehicle approach threats.
- **System-Wide Floating Overlay (`SYSTEM_ALERT_WINDOW`)**:
  - **Telemetry Widget**: A compact, draggable floating window showing live power, 3s power, heart rate, and cadence over any navigation, workout, or video application.
  - **Radar Threat Sidebar**: A vertical visual threat bar displaying approaching rear vehicles in real time.
  - **Position Persistence**: Drag positions of overlay widgets are saved automatically across sessions.
- **BLE Sensor Scanning & Slot Management**: Easily scan for nearby BLE devices, pair them, and assign them to dedicated slots with auto-connect functionality.
- **GPS Speed Integration**: Built-in location manager providing real-time GPS speed tracking.
- **Foreground Service**: Keeps sensor connections alive and updates overlay telemetry continuously via `OverlayService`.

---

## 📡 Supported Sensors & Protocols

| Sensor Type | GATT Service UUID | Characteristics / Protocols Supported | Metrics Extracted |
| :--- | :--- | :--- | :--- |
| **Cycling Power Meter** | `0x1818` | `0x2A63` (Cycling Power Measurement) | Instant Power (W), 3s Avg Power, Cadence (RPM) |
| **Heart Rate Monitor** | `0x180D` | `0x2A37` (Heart Rate Measurement) | Heart Rate (BPM) |
| **Rear Cycling Radar** | `0x183C` / Garmin Varia (`6e400001-...`) | `0x2B18`, Varia custom characteristics | Vehicle Distance, Speed, Threat Levels |

---

## 🛠️ Tech Stack & Architecture

- **Language & Runtime**: Kotlin, Java 11, Kotlin Coroutines & Flow
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Navigation**: Navigation3 (`androidx.navigation3`)
- **Dependency Injection**: [Koin](https://insert-koin.io/)
- **Data Persistence**: Room, DataStore Preferences, SharedPreferences
- **Networking & Serialization**: Retrofit, Moshi, KotlinX Serialization
- **Logging & Crash Analytics**: Timber, Firebase Analytics, Firebase Crashlytics
- **Target Android SDK**: minSdk 33 (Android 13+), targetSdk 37

---

## 🔑 Permissions Required

- **Bluetooth**: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (To discover and connect to BLE cycling sensors)
- **Location**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (For GPS speed tracking and BLE scanning)
- **Display over other apps**: `SYSTEM_ALERT_WINDOW` (Required to render the floating overlay widgets over other apps)
- **Foreground Service**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` (To maintain active sensor data streams in the background)
- **Notifications**: `POST_NOTIFICATIONS` (To show ongoing service notification controls)

---

## 📱 Usage Guide

### 1. Initial Setup & Permissions
Launch the app and grant the requested permissions:
- Bluetooth and Location access for sensor scanning.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`)** permission to enable floating overlay widgets.

### 2. Scanning & Pairing Sensors
1. Navigate to the **Sensors** tab.
2. Tap **Scan** to search for nearby BLE sensors.
3. Assign discovered devices to their respective slots:
   - **Power Meter Slot**
   - **Heart Rate Slot**
   - **Radar Slot**
4. Enable **Auto-Connect** for paired slots to automatically reconnect on startup.

### 3. Using the Live Dashboard
Navigate to the **Dashboard** tab to view real-time metrics, including:
- Current Power & 3-Second Average Power
- Heart Rate & Cadence
- GPS Speed
- Rear Radar Traffic Threat Indicators

### 4. Enabling the Floating Overlay
1. From the **Dashboard**, toggle **Enable Floating Overlay**.
2. The `OverlayService` foreground service starts and displays the draggable overlay widgets over your screen.
3. Drag the overlay widget or radar sidebar anywhere on screen; positions are saved automatically.
4. Toggle off the overlay from the Dashboard or notification drawer to stop the foreground service.

---

## 🛠️ Building & Running

### Prerequisites
- Android Studio Ladybug or newer
- JDK 11
- Android device or emulator running Android 13 (API level 33) or higher with Bluetooth capabilities

### Build Commands

```bash
# Clone the repository
git clone https://github.com/username/CyclingSensorsOverlay.git
cd CyclingSensorsOverlay

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

---

## 📂 Project Structure

```
CyclingSensorsOverlay/
├── app/
│   ├── src/main/java/cz/novotny/cyclingsensorsoverlay/
│   │   ├── data/            # BLE managers, parsers, GPS location manager, repositories
│   │   ├── di/              # Koin dependency injection modules
│   │   ├── domain/          # Models, repository interfaces, use cases
│   │   ├── service/         # OverlayService foreground service
│   │   ├── ui/              # Compose screens (Dashboard, Scanner, Overlay widgets, Theme)
│   │   └── util/            # App loggers, permission helpers, overlay lifecycle
│   └── src/test/            # Unit tests for parsers, use cases, viewmodels
├── gradle/                  # Gradle wrapper & version catalog (libs.versions.toml)
├── build.gradle.kts         # Root build configuration
└── settings.gradle.kts      # Subproject settings
```

---

## 📄 License

This project is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE).
You are free to use, copy, modify, and distribute this software for **personal and non-commercial purposes only**. Commercial distribution, sale, or monetization is strictly prohibited.
