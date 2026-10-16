package com.coincide.drive.drive

import android.content.Context
import com.coincide.drive.data.AppDatabase
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.sync.Differential2WaySyncEngine
import com.coincide.drive.sync.LocalSafManager
import com.coincide.drive.work.DriveSyncWorker
import com.coincide.drive.work.SyncEngineDelegate
import com.coincide.drive.work.SyncResult

/**
 * High-level entry point and bridge connecting the Drive Integration and WorkManager layer.
 * Implements [SyncEngineDelegate] to execute differential synchronization cycles for sync pairs.
 */
class DifferentialSyncEngine(
    private val providedContext: Context? = null
) : SyncEngineDelegate {

    companion object {
        /**
         * Installs this sync engine delegate into [DriveSyncWorker] for background execution.
         */
        fun installDelegate(context: Context) {
            DriveSyncWorker.syncEngineDelegate = DifferentialSyncEngine(context.applicationContext)
        }
    }

    /**
     * Executes synchronization for a single pair using the application context.
     * Accessible directly or via reflection by [DriveSyncWorker].
     */
    suspend fun syncPair(context: Context, pair: SyncPairEntity): SyncResult {
        return executeSyncWithProgress(context, pair, null)
    }

    override suspend fun executeSync(pair: SyncPairEntity, onProgress: (String) -> Unit): SyncResult {
        val ctx = checkNotNull(providedContext) {
            "Context must be provided to DifferentialSyncEngine instance"
        }
        return executeSyncWithProgress(ctx, pair, onProgress)
    }

    suspend fun executeSyncWithProgress(
        context: Context,
        pair: SyncPairEntity,
        onProgress: ((String) -> Unit)?
    ): SyncResult {
        val authManager = GoogleAuthManager(context)
        val credential = if (pair.accountEmail.isNotBlank()) {
            authManager.getCredentialForEmail(pair.accountEmail)
        } else {
            authManager.getCredential()
        } ?: return SyncResult(
            filesUploaded = 0,
            filesDownloaded = 0,
            conflictsResolved = 0,
            isSuccess = false,
            errorMessage = "Google Account not authenticated. Please sign in via Account Picker."
        )

        val driveRepository = DriveRepository(credential)
        val localSafManager = LocalSafManager(context)
        val database = AppDatabase.getInstance(context)
        val fileMetadataDao = database.fileMetadataDao()
        val syncLogDao = database.syncLogDao()

        val engine = Differential2WaySyncEngine(
            driveRepository = driveRepository,
            localSafManager = localSafManager,
            fileMetadataDao = fileMetadataDao,
            syncLogDao = syncLogDao
        )

        val engineResult = engine.sync(pair) { progress ->
            onProgress?.invoke(progress.currentAction)
        }

        return SyncResult(
            filesUploaded = engineResult.filesUploaded,
            filesDownloaded = engineResult.filesDownloaded,
            conflictsResolved = engineResult.conflictsResolved,
            isSuccess = engineResult.isSuccess,
            errorMessage = if (engineResult.errors.isNotEmpty()) engineResult.errors.joinToString("; ") else null
        )
    }
}
