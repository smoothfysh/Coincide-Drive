# Coincide Drive 🔄

A native Android application providing seamless, bidirectional synchronization between Google Drive folders and local device directories via the Android Storage Access Framework (SAF).

Designed for productivity workflows (such as Obsidian vaults, document archives, and media libraries) where public folder access is required so other Android apps can read and edit synced files simultaneously.

---

## ✨ Features

- **Google Play Services Authentication:** Direct authentication using system accounts configured on the Android device without custom web OAuth redirects.
- **Storage Access Framework (SAF):** Full user-controlled authorization (`ACTION_OPEN_DOCUMENT_TREE`) to public directories (`Documents/`, `Download/`, etc.) ensuring cross-app interoperability.
- **Differential 2-Way Sync Engine:** Computes differential file hashes (SHA-256) and modified timestamps stored in a local Room SQLite database to minimize unnecessary data transfers.
- **"Keep Both" Conflict Resolution:** Lossless conflict handling. If a file is modified both locally and on Google Drive between sync cycles, the incoming file is preserved with a timestamp suffix (`filename_conflict_YYYYMMDD_HHMMSS.ext`).
- **Android WorkManager Engine:** Scheduled periodic sync running via the Android OS `JobScheduler`, featuring customizable constraints:
  - Wi-Fi only (`NetworkType.UNMETERED`)
  - Battery charging only (`requiresCharging`)
  - Mobile data allow / block data roaming (`NetworkType.NOT_ROAMING`)
  - Flexible sync intervals (15m, 30m, 1h, 6h, 12h, 24h, or Manual)
- **Background Execution & Battery Optimization Exemption:** One-tap integration with `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to ensure sync tasks survive Android Doze mode and manufacturer battery savers.
- **Live Sync Dashboard:** Real-time sync state indicators, immediate manual sync trigger, active folder pairing summary, and audit logs.

---

## 🛠️ Architecture & Tech Stack

- **UI:** Jetpack Compose with Material Design 3 and Edge-to-Edge display.
- **Architecture Pattern:** Single-Activity MVI/MVVM with reactive unidirectional data flow.
- **Local Database:** Room SQLite with Kotlin Coroutines and Flows (`SyncPairDao`, `FileMetadataDao`, `SyncLogDao`).
- **Preferences:** Jetpack DataStore Preferences for reactive settings management.
- **Background Processing:** Android WorkManager (`CoroutineWorker`) with Android 14+ Foreground Service data-sync support (`FOREGROUND_SERVICE_DATA_SYNC`).
- **Drive Integration:** Google Drive REST API v3 with Google Play Services authentication.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or JDK 21
- Android SDK 35 (Android 15) with minimum SDK 26 (Android 8.0)

### Building & Running
1. Clone the repository:
   ```bash
   git clone https://github.com/smoothfysh/Coincide-Drive.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and ensure dependencies are resolved.
4. Run on a physical Android device or emulator with Google Play Services enabled:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 License
This project is licensed under the Apache 2.0 License - see the LICENSE file for details.
