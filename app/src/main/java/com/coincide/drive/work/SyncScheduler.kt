package com.coincide.drive.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.coincide.drive.data.preferences.SyncPreferences
import java.util.concurrent.TimeUnit

/**
 * Helper scheduler managing Periodic and One-Time WorkManager requests for DriveSyncWorker.
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"
    const val PERIODIC_WORK_NAME = "coincide_drive_periodic_sync"
    const val IMMEDIATE_WORK_NAME = "coincide_drive_immediate_sync"
    const val WORK_TAG_SYNC = "tag_drive_sync"

    /**
     * Builds Android [Constraints] based on the user's [SyncPreferences].
     */
    fun buildConstraints(preferences: SyncPreferences): Constraints {
        val networkType = when {
            preferences.wifiOnly -> NetworkType.UNMETERED
            preferences.blockRoaming -> NetworkType.NOT_ROAMING
            preferences.allowMobileData -> NetworkType.CONNECTED
            else -> NetworkType.CONNECTED
        }

        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(preferences.onlyWhenCharging)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    /**
     * Schedules or updates periodic synchronization according to user preferences.
     * WorkManager requires a minimum interval of 15 minutes.
     * If intervalMinutes <= 0 (Manual Only), any existing periodic work is cancelled.
     */
    fun schedulePeriodicSync(context: Context, preferences: SyncPreferences) {
        val workManager = WorkManager.getInstance(context)

        if (preferences.syncIntervalMinutes <= 0L) {
            Log.i(TAG, "Sync interval set to manual only. Cancelling periodic sync work.")
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val intervalMinutes = maxOf(15L, preferences.syncIntervalMinutes)
        val constraints = buildConstraints(preferences)

        val periodicRequest = PeriodicWorkRequestBuilder<DriveSyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES,
            // 5-minute flex interval for battery efficiency
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(WORK_TAG_SYNC)
            .build()

        Log.i(TAG, "Enqueuing periodic sync work with interval=${intervalMinutes}m and constraints=$constraints")
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    /**
     * Triggers an immediate "Sync Now" one-time execution, executed as an expedited
     * work request when possible.
     */
    fun scheduleImmediateSync(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val immediateRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_SYNC)
            .build()

        Log.i(TAG, "Scheduling immediate sync request with ExistingWorkPolicy.REPLACE")
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    /**
     * Cancels scheduled periodic sync work.
     */
    fun cancelPeriodicSync(context: Context) {
        Log.i(TAG, "Cancelling periodic sync work.")
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /**
     * Cancels all scheduled or running sync work (both periodic and one-time).
     */
    fun cancelAllSync(context: Context) {
        Log.i(TAG, "Cancelling all sync operations.")
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        workManager.cancelAllWorkByTag(WORK_TAG_SYNC)
    }
}
