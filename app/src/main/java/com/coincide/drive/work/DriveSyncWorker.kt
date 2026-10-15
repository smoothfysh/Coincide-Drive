package com.coincide.drive.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.coincide.drive.data.AppDatabase
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.data.preferences.SyncPreferences
import com.coincide.drive.data.preferences.SyncPreferencesManager
import com.coincide.drive.drive.DriveRepository
import com.coincide.drive.drive.GoogleAuthManager
import com.coincide.drive.sync.Differential2WaySyncEngine
import com.coincide.drive.sync.LocalSafManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data class produced by a sync execution cycle.
 */
data class SyncResult(
    val filesUploaded: Int = 0,
    val filesDownloaded: Int = 0,
    val conflictsResolved: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Interface allowing custom or mocked Sync Engines to plug into the background worker.
 */
fun interface SyncEngineDelegate {
    suspend fun executeSync(pair: SyncPairEntity, onProgress: (String) -> Unit): SyncResult
}

/**
 * WorkManager CoroutineWorker responsible for coordinating background synchronization.
 * Handles Android 14+ foreground service notifications (FOREGROUND_SERVICE_TYPE_DATA_SYNC),
 * dynamic constraint checks, invokes Differential2WaySyncEngine, and records audit logs to Room.
 */
class DriveSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(applicationContext) }
    private val preferencesManager: SyncPreferencesManager by lazy {
        SyncPreferencesManager.getInstance(applicationContext)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting DriveSyncWorker execution (id=$id)")
        val preferences = preferencesManager.getSyncPreferences()

        // Display foreground notification (required on Android 14+ when running as dataSync foreground service)
        try {
            setForeground(createForegroundInfo("Preparing background sync..."))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info (running as standard background job): ${e.message}")
        }

        preferencesManager.setSyncInProgress(true)

        try {
            // Check dynamic constraints during execution
            if (!satisfiesDynamicConstraints(preferences)) {
                Log.w(TAG, "Dynamic execution constraints violated. Postponing sync.")
                return@withContext Result.retry()
            }

            val activePairs = database.syncPairDao().getActivePairs()
            if (activePairs.isEmpty()) {
                Log.i(TAG, "No active sync pairs configured. Completed successfully.")
                return@withContext Result.success()
            }

            var allPairsSucceeded = true

            for (pair in activePairs) {
                if (isStopped) {
                    Log.w(TAG, "Worker execution was cancelled or stopped.")
                    return@withContext Result.retry()
                }

                val startTime = System.currentTimeMillis()
                val progressText = "Syncing ${pair.remoteFolderName}..."

                try {
                    setForeground(createForegroundInfo(progressText))
                } catch (e: Exception) {
                    Log.d(TAG, "Foreground update skipped: ${e.message}")
                }

                setProgress(
                    workDataOf(
                        KEY_CURRENT_PAIR_ID to pair.id,
                        KEY_CURRENT_PAIR_NAME to pair.remoteFolderName,
                        KEY_STATUS to "IN_PROGRESS"
                    )
                )

                // Invoke the sync engine
                val result = runSyncForPair(pair) { detail ->
                    try {
                        updateNotification("${pair.remoteFolderName}: $detail")
                    } catch (_: Exception) {}
                }

                val duration = System.currentTimeMillis() - startTime
                val logStatus = if (result.isSuccess) {
                    SyncLogEntity.STATUS_SUCCESS
                } else {
                    allPairsSucceeded = false
                    SyncLogEntity.STATUS_FAILED
                }

                // Record audit log entry in Room database
                val syncLog = SyncLogEntity(
                    pairId = pair.id,
                    timestamp = System.currentTimeMillis(),
                    filesUploaded = result.filesUploaded,
                    filesDownloaded = result.filesDownloaded,
                    conflictsResolved = result.conflictsResolved,
                    status = logStatus,
                    errorMessage = result.errorMessage,
                    durationMs = duration
                )
                database.syncLogDao().insertLog(syncLog)
            }

            val finalTimestamp = System.currentTimeMillis()
            preferencesManager.setLastSyncTimestamp(finalTimestamp)

            setProgress(
                workDataOf(
                    KEY_STATUS to if (allPairsSucceeded) "COMPLETED" else "COMPLETED_WITH_ERRORS",
                    KEY_TIMESTAMP to finalTimestamp
                )
            )

            if (allPairsSucceeded) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync run encountered unhandled exception: ${e.message}", e)
            Result.failure()
        } finally {
            preferencesManager.setSyncInProgress(false)
            Log.i(TAG, "DriveSyncWorker finished.")
        }
    }

    private suspend fun runSyncForPair(
        pair: SyncPairEntity,
        onProgress: (String) -> Unit
    ): SyncResult {
        val delegate = syncEngineDelegate
        if (delegate != null) {
            return delegate.executeSync(pair, onProgress)
        }

        return try {
            val authManager = GoogleAuthManager(applicationContext)
            val credential = if (pair.accountEmail.isNotBlank()) {
                authManager.getCredentialForEmail(pair.accountEmail)
            } else {
                authManager.getCredential()
            }
            if (credential == null) {
                Log.w(TAG, "No authenticated Google Account found for sync.")
                return SyncResult(
                    filesUploaded = 0,
                    filesDownloaded = 0,
                    conflictsResolved = 0,
                    isSuccess = false,
                    errorMessage = "No authenticated Google Account active on device."
                )
            }

            val driveRepo = DriveRepository(credential)
            val safManager = LocalSafManager(applicationContext)
            // Pass syncLogDao = null because DriveSyncWorker manages logging centrally
            val engine = Differential2WaySyncEngine(
                driveRepository = driveRepo,
                localSafManager = safManager,
                fileMetadataDao = database.fileMetadataDao(),
                syncLogDao = null
            )

            val engineResult = engine.sync(pair) { progress ->
                onProgress(progress.currentAction)
            }

            SyncResult(
                filesUploaded = engineResult.filesUploaded,
                filesDownloaded = engineResult.filesDownloaded,
                conflictsResolved = engineResult.conflictsResolved,
                isSuccess = engineResult.isSuccess,
                errorMessage = if (engineResult.errors.isNotEmpty()) engineResult.errors.joinToString("\n") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling Differential2WaySyncEngine: ${e.message}", e)
            SyncResult(
                filesUploaded = 0,
                filesDownloaded = 0,
                conflictsResolved = 0,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private fun satisfiesDynamicConstraints(preferences: SyncPreferences): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        if (capabilities == null) {
            Log.w(TAG, "No active network connectivity detected.")
            return false
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false

        // Check Wi-Fi / unmetered constraint
        if (preferences.wifiOnly) {
            val isUnmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (!isUnmetered && !isWifi) {
                Log.w(TAG, "Network is metered while wifiOnly constraint is enabled.")
                return false
            }
        }

        // Check Roaming constraint
        if (preferences.blockRoaming) {
            val isNotRoaming = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            if (!isNotRoaming) {
                Log.w(TAG, "Network is currently roaming while blockRoaming constraint is enabled.")
                return false
            }
        }

        // Check Charging constraint
        if (preferences.onlyWhenCharging) {
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val isCharging = batteryManager?.isCharging ?: false
            if (!isCharging) {
                Log.w(TAG, "Device is not charging while onlyWhenCharging constraint is enabled.")
                return false
            }
        }

        return true
    }

    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Coincide Drive Sync")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progressText: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Coincide Drive Sync")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Drive Synchronization",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live synchronization progress between Google Drive and local storage."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "DriveSyncWorker"
        const val NOTIFICATION_CHANNEL_ID = "coincide_drive_sync_channel"
        const val NOTIFICATION_ID = 2001

        const val KEY_CURRENT_PAIR_ID = "current_pair_id"
        const val KEY_CURRENT_PAIR_NAME = "current_pair_name"
        const val KEY_STATUS = "sync_status"
        const val KEY_TIMESTAMP = "sync_timestamp"

        @Volatile
        var syncEngineDelegate: SyncEngineDelegate? = null
    }
}
