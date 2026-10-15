package com.coincide.drive

import android.app.Application
import com.coincide.drive.data.AppDatabase
import com.coincide.drive.data.preferences.SyncPreferencesManager
import com.coincide.drive.drive.GoogleAuthManager

/**
 * Base Application class for Coincide Drive.
 * Provides singleton container access to Room Database, DataStore, and Auth.
 */
class CoincideApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: SyncPreferencesManager by lazy { SyncPreferencesManager.getInstance(this) }
    val authManager: GoogleAuthManager by lazy { GoogleAuthManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: CoincideApplication
            private set
    }
}
