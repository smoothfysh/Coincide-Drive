package com.coincide.drive.sync

import android.net.Uri
import com.coincide.drive.data.dao.FileMetadataDao
import com.coincide.drive.data.dao.SyncLogDao
import com.coincide.drive.data.entity.FileMetadataEntity
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.drive.DriveFileItem
import com.coincide.drive.drive.DriveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Progress update emitted during differential synchronization.
 */
data class SyncProgress(
    val currentFile: String = "",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val currentAction: String = "Initializing"
)

/**
 * Result summary produced after synchronization completes.
 */
data class SyncResult(
    val pairId: String,
    val filesUploaded: Int,
    val filesDownloaded: Int,
    val conflictsResolved: Int,
    val filesDeleted: Int,
    val isSuccess: Boolean,
    val errors: List<String>,
    val durationMs: Long
)

/**
 * Differential 3-Way Synchronization Engine for Coincide Drive.
 *
 * Compares:
 * 1. Remote Google Drive state (id, name, modifiedTime, md5Checksum)
 * 2. Local SAF state (DocumentFile uri, name, lastModified, computed MD5)
 * 3. Baseline snapshot stored in Room DB ([FileMetadataEntity])
 *
 * Handles:
 * - Case A: Remote modified -> Download to local SAF
 * - Case B: Local modified -> Upload to Drive
 * - Case C: New Remote -> Download to local SAF
 * - Case D: New Local -> Upload to Drive
 * - Case E: Remote deleted -> Delete local SAF file
 * - Case F: Local deleted -> Trash remote Drive file
 * - Case G: Conflict (Both changed) -> Delegate to [KeepBothConflictResolver]
 */
class Differential2WaySyncEngine(
    private val driveRepository: DriveRepository,
    private val localSafManager: LocalSafManager,
    private val fileMetadataDao: FileMetadataDao,
    private val syncLogDao: SyncLogDao? = null,
    private val conflictResolver: KeepBothConflictResolver = KeepBothConflictResolver(
        driveRepository,
        localSafManager,
        fileMetadataDao
    )
) {

    /**
     * Executes a full 2-way differential synchronization cycle for the given [syncPair].
     *
     * @param syncPair The pair configuration defining remote folder and local SAF tree.
     * @param onProgress Optional callback invoked with current progress milestones.
     * @return [SyncResult] detailing sync statistics and outcomes.
     */
    suspend fun sync(
        syncPair: SyncPairEntity,
        onProgress: ((SyncProgress) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var filesUploaded = 0
        var filesDownloaded = 0
        var conflictsResolved = 0
        var filesDeleted = 0
        val errors = mutableListOf<String>()

        try {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(SyncProgress(currentAction = "Scanning remote Google Drive files..."))

            val treeUri = Uri.parse(syncPair.localTreeUri)

            // 1. Scan Remote Google Drive files recursively
            val remoteFolderCache = mutableMapOf<String, String>() // relativeDirPath -> folderId
            remoteFolderCache[""] = syncPair.remoteFolderId
            val remoteFilesMap = scanRemoteHierarchy(
                folderId = syncPair.remoteFolderId,
                currentRelativePath = "",
                folderCache = remoteFolderCache
            )

            currentCoroutineContext().ensureActive()
            onProgress?.invoke(SyncProgress(currentAction = "Scanning local SAF storage..."))

            // 2. Scan Local SAF files recursively
            val localFilesList = localSafManager.listAllFiles(treeUri, computeHashes = true)
            val localFilesMap = localFilesList.associateBy { it.relativePath }

            currentCoroutineContext().ensureActive()
            onProgress?.invoke(SyncProgress(currentAction = "Querying database baseline snapshot..."))

            // 3. Load baseline metadata from Room
            val baselines = fileMetadataDao.getByPairId(syncPair.id)
            val baselineMap = baselines.associateBy { it.relativePath }

            // 4. Collect all distinct relative paths across Remote, Local, and Baseline
            val allPaths = (remoteFilesMap.keys + localFilesMap.keys + baselineMap.keys).toSortedSet()
            val totalFiles = allPaths.size
            var processed = 0

            for (relativePath in allPaths) {
                currentCoroutineContext().ensureActive()
                val remote = remoteFilesMap[relativePath]
                val local = localFilesMap[relativePath]
                val baseline = baselineMap[relativePath]
                val fileName = relativePath.substringAfterLast("/")

                processed++
                onProgress?.invoke(
                    SyncProgress(
                        currentFile = relativePath,
                        processedCount = processed,
                        totalCount = totalFiles,
                        currentAction = "Analyzing $fileName"
                    )
                )

                try {
                    when {
                        // -------------------------------------------------------------
                        // Baseline Exists (File was previously tracked)
                        // -------------------------------------------------------------
                        baseline != null -> {
                            when {
                                remote != null && local != null -> {
                                    val remoteChanged = isRemoteChanged(remote, baseline)
                                    val localChanged = isLocalChanged(local, baseline)

                                    when {
                                        // Case G: Conflict - Both modified
                                        remoteChanged && localChanged -> {
                                            if (remote.md5Checksum != null && remote.md5Checksum == local.md5Hash) {
                                                // Content identical: converge without conflict
                                                updateBaselineSynchronized(syncPair.id, relativePath, remote, local)
                                            } else {
                                                onProgress?.invoke(
                                                    SyncProgress(
                                                        currentFile = relativePath,
                                                        processedCount = processed,
                                                        totalCount = totalFiles,
                                                        currentAction = "Resolving conflict for $fileName (Keep Both)"
                                                    )
                                                )
                                                val parentFolderId = resolveRemoteParentFolderId(
                                                    rootFolderId = syncPair.remoteFolderId,
                                                    relativePath = relativePath,
                                                    folderCache = remoteFolderCache
                                                )
                                                conflictResolver.resolveConflict(
                                                    pairId = syncPair.id,
                                                    treeUri = treeUri,
                                                    remoteParentFolderId = parentFolderId,
                                                    originalRelativePath = relativePath,
                                                    remoteItem = remote,
                                                    localItem = local,
                                                    baseline = baseline
                                                )
                                                conflictsResolved++
                                                filesDownloaded++
                                                filesUploaded++
                                            }
                                        }

                                        // Case A: Remote changed, Local unchanged -> Download
                                        remoteChanged && !localChanged -> {
                                            onProgress?.invoke(
                                                SyncProgress(
                                                    currentFile = relativePath,
                                                    processedCount = processed,
                                                    totalCount = totalFiles,
                                                    currentAction = "Downloading updated $fileName"
                                                )
                                            )
                                            downloadRemoteToLocal(
                                                treeUri = treeUri,
                                                relativePath = relativePath,
                                                remote = remote,
                                                local = local,
                                                pairId = syncPair.id,
                                                baseline = baseline
                                            )
                                            filesDownloaded++
                                        }

                                        // Case B: Local changed, Remote unchanged -> Upload
                                        !remoteChanged && localChanged -> {
                                            onProgress?.invoke(
                                                SyncProgress(
                                                    currentFile = relativePath,
                                                    processedCount = processed,
                                                    totalCount = totalFiles,
                                                    currentAction = "Uploading updated $fileName"
                                                )
                                            )
                                            uploadLocalToRemoteUpdate(
                                                pairId = syncPair.id,
                                                relativePath = relativePath,
                                                local = local,
                                                remote = remote,
                                                baseline = baseline
                                            )
                                            filesUploaded++
                                        }

                                        // Both unchanged -> In sync
                                        else -> {
                                            // No action needed
                                        }
                                    }
                                }

                                // Case E: Remote was deleted
                                remote == null && local != null -> {
                                    val localChanged = isLocalChanged(local, baseline)
                                    if (!localChanged) {
                                        onProgress?.invoke(
                                            SyncProgress(
                                                currentFile = relativePath,
                                                processedCount = processed,
                                                totalCount = totalFiles,
                                                currentAction = "Deleting local $fileName (Remote deleted)"
                                            )
                                        )
                                        localSafManager.deleteFile(local.uri)
                                        fileMetadataDao.delete(baseline)
                                        filesDeleted++
                                    } else {
                                        // Lossless safeguard: User edited locally while remote was deleted.
                                        // Re-upload modified local file to Remote
                                        val parentFolderId = resolveRemoteParentFolderId(
                                            rootFolderId = syncPair.remoteFolderId,
                                            relativePath = relativePath,
                                            folderCache = remoteFolderCache
                                        )
                                        uploadNewLocalToRemote(
                                            pairId = syncPair.id,
                                            treeUri = treeUri,
                                            parentFolderId = parentFolderId,
                                            relativePath = relativePath,
                                            local = local
                                        )
                                        filesUploaded++
                                    }
                                }

                                // Case F: Local was deleted
                                remote != null && local == null -> {
                                    val remoteChanged = isRemoteChanged(remote, baseline)
                                    if (!remoteChanged) {
                                        onProgress?.invoke(
                                            SyncProgress(
                                                currentFile = relativePath,
                                                processedCount = processed,
                                                totalCount = totalFiles,
                                                currentAction = "Trashing remote $fileName (Local deleted)"
                                            )
                                        )
                                        driveRepository.trashFile(remote.id)
                                        fileMetadataDao.delete(baseline)
                                        filesDeleted++
                                    } else {
                                        // Lossless safeguard: Remote was edited while local was deleted.
                                        // Re-download remote file to Local
                                        downloadRemoteToLocal(
                                            treeUri = treeUri,
                                            relativePath = relativePath,
                                            remote = remote,
                                            local = null,
                                            pairId = syncPair.id,
                                            baseline = baseline
                                        )
                                        filesDownloaded++
                                    }
                                }

                                // Both deleted
                                remote == null && local == null -> {
                                    fileMetadataDao.delete(baseline)
                                }
                            }
                        }

                        // -------------------------------------------------------------
                        // No Baseline (New files)
                        // -------------------------------------------------------------
                        baseline == null -> {
                            when {
                                // Both exist but not indexed yet
                                remote != null && local != null -> {
                                    if (remote.md5Checksum != null && remote.md5Checksum == local.md5Hash) {
                                        updateBaselineSynchronized(syncPair.id, relativePath, remote, local)
                                    } else {
                                        val parentFolderId = resolveRemoteParentFolderId(
                                            rootFolderId = syncPair.remoteFolderId,
                                            relativePath = relativePath,
                                            folderCache = remoteFolderCache
                                        )
                                        conflictResolver.resolveConflict(
                                            pairId = syncPair.id,
                                            treeUri = treeUri,
                                            remoteParentFolderId = parentFolderId,
                                            originalRelativePath = relativePath,
                                            remoteItem = remote,
                                            localItem = local,
                                            baseline = null
                                        )
                                        conflictsResolved++
                                        filesDownloaded++
                                        filesUploaded++
                                    }
                                }

                                // Case C: New Remote file -> Download
                                remote != null && local == null -> {
                                    onProgress?.invoke(
                                        SyncProgress(
                                            currentFile = relativePath,
                                            processedCount = processed,
                                            totalCount = totalFiles,
                                            currentAction = "Downloading new remote file $fileName"
                                        )
                                    )
                                    downloadRemoteToLocal(
                                        treeUri = treeUri,
                                        relativePath = relativePath,
                                        remote = remote,
                                        local = null,
                                        pairId = syncPair.id,
                                        baseline = null
                                    )
                                    filesDownloaded++
                                }

                                // Case D: New Local file -> Upload
                                remote == null && local != null -> {
                                    onProgress?.invoke(
                                        SyncProgress(
                                            currentFile = relativePath,
                                            processedCount = processed,
                                            totalCount = totalFiles,
                                            currentAction = "Uploading new local file $fileName"
                                        )
                                    )
                                    val parentFolderId = resolveRemoteParentFolderId(
                                        rootFolderId = syncPair.remoteFolderId,
                                        relativePath = relativePath,
                                        folderCache = remoteFolderCache
                                    )
                                    uploadNewLocalToRemote(
                                        pairId = syncPair.id,
                                        treeUri = treeUri,
                                        parentFolderId = parentFolderId,
                                        relativePath = relativePath,
                                        local = local
                                    )
                                    filesUploaded++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Error syncing $relativePath: ${e.message}")
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val result = SyncResult(
                pairId = syncPair.id,
                filesUploaded = filesUploaded,
                filesDownloaded = filesDownloaded,
                conflictsResolved = conflictsResolved,
                filesDeleted = filesDeleted,
                isSuccess = errors.isEmpty(),
                errors = errors,
                durationMs = duration
            )

            // Record execution log in database
            syncLogDao?.insert(
                SyncLogEntity(
                    pairId = syncPair.id,
                    timestamp = System.currentTimeMillis(),
                    filesUploaded = filesUploaded,
                    filesDownloaded = filesDownloaded,
                    conflictsResolved = conflictsResolved,
                    status = if (errors.isEmpty()) SyncLogEntity.STATUS_SUCCESS else SyncLogEntity.STATUS_FAILED,
                    errorMessage = if (errors.isNotEmpty()) errors.joinToString("\n") else null,
                    durationMs = duration
                )
            )

            onProgress?.invoke(
                SyncProgress(
                    processedCount = totalFiles,
                    totalCount = totalFiles,
                    currentAction = "Sync completed in ${duration}ms"
                )
            )

            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            errors.add("Fatal sync failure: ${e.message}")
            val failedResult = SyncResult(
                pairId = syncPair.id,
                filesUploaded = filesUploaded,
                filesDownloaded = filesDownloaded,
                conflictsResolved = conflictsResolved,
                filesDeleted = filesDeleted,
                isSuccess = false,
                errors = errors,
                durationMs = duration
            )

            syncLogDao?.insert(
                SyncLogEntity(
                    pairId = syncPair.id,
                    timestamp = System.currentTimeMillis(),
                    filesUploaded = filesUploaded,
                    filesDownloaded = filesDownloaded,
                    conflictsResolved = conflictsResolved,
                    status = SyncLogEntity.STATUS_FAILED,
                    errorMessage = e.message ?: "Unknown fatal error",
                    durationMs = duration
                )
            )

            failedResult
        }
    }

    private suspend fun scanRemoteHierarchy(
        folderId: String,
        currentRelativePath: String,
        folderCache: MutableMap<String, String>
    ): Map<String, DriveFileItem> {
        val resultMap = mutableMapOf<String, DriveFileItem>()

        // 1. Fetch files in current folder
        val files = driveRepository.listFiles(folderId)
        for (file in files) {
            val relPath = if (currentRelativePath.isEmpty()) file.name else "$currentRelativePath/${file.name}"
            resultMap[relPath] = file
        }

        // 2. Fetch subfolders and recursively traverse
        val subfolders = driveRepository.listFolders(folderId)
        for (folder in subfolders) {
            val subfolderPath = if (currentRelativePath.isEmpty()) folder.name else "$currentRelativePath/${folder.name}"
            folderCache[subfolderPath] = folder.id
            val subFiles = scanRemoteHierarchy(folder.id, subfolderPath, folderCache)
            resultMap.putAll(subFiles)
        }

        return resultMap
    }

    private fun isRemoteChanged(remote: DriveFileItem, baseline: FileMetadataEntity): Boolean {
        return if (!remote.md5Checksum.isNullOrEmpty() && !baseline.remoteMd5.isNullOrEmpty()) {
            remote.md5Checksum != baseline.remoteMd5
        } else {
            remote.modifiedTime != baseline.remoteModifiedTime
        }
    }

    private fun isLocalChanged(local: LocalFileItem, baseline: FileMetadataEntity): Boolean {
        return if (!local.md5Hash.isNullOrEmpty() && !baseline.localMd5.isNullOrEmpty()) {
            local.md5Hash != baseline.localMd5
        } else {
            local.lastModified != baseline.localModifiedTime
        }
    }

    private suspend fun downloadRemoteToLocal(
        treeUri: Uri,
        relativePath: String,
        remote: DriveFileItem,
        local: LocalFileItem?,
        pairId: String,
        baseline: FileMetadataEntity?
    ) {
        val docFile = checkNotNull(
            localSafManager.createOrGetFile(treeUri, relativePath, remote.mimeType)
        ) { "Failed to create local SAF file for $relativePath" }

        val outputStream = checkNotNull(
            localSafManager.openOutputStream(docFile.uri)
        ) { "Failed to open OutputStream for ${docFile.uri}" }

        outputStream.use { out ->
            driveRepository.downloadFile(remote.id, out)
        }

        val computedMd5 = localSafManager.calculateMd5(docFile.uri) ?: remote.md5Checksum
        val now = System.currentTimeMillis()

        val updatedBaseline = FileMetadataEntity(
            id = baseline?.id ?: UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = relativePath,
            fileName = relativePath.substringAfterLast("/"),
            remoteFileId = remote.id,
            remoteMd5 = remote.md5Checksum ?: computedMd5,
            remoteModifiedTime = remote.modifiedTime,
            localUri = docFile.uri.toString(),
            localMd5 = computedMd5,
            localModifiedTime = docFile.lastModified(),
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(updatedBaseline)
    }

    private suspend fun uploadLocalToRemoteUpdate(
        pairId: String,
        relativePath: String,
        local: LocalFileItem,
        remote: DriveFileItem,
        baseline: FileMetadataEntity?
    ) {
        val inputStream = checkNotNull(
            localSafManager.openInputStream(local.uri)
        ) { "Failed to open InputStream for ${local.uri}" }

        val updatedRemote = inputStream.use { inStream ->
            driveRepository.updateFile(
                fileId = remote.id,
                inputStream = inStream,
                mimeType = local.mimeType
            )
        }

        val localMd5 = local.md5Hash ?: localSafManager.calculateMd5(local.uri)
        val now = System.currentTimeMillis()

        val updatedBaseline = FileMetadataEntity(
            id = baseline?.id ?: UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = relativePath,
            fileName = local.name,
            remoteFileId = updatedRemote.id,
            remoteMd5 = updatedRemote.md5Checksum ?: localMd5,
            remoteModifiedTime = updatedRemote.modifiedTime,
            localUri = local.uri.toString(),
            localMd5 = localMd5,
            localModifiedTime = local.lastModified,
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(updatedBaseline)
    }

    private suspend fun uploadNewLocalToRemote(
        pairId: String,
        treeUri: Uri,
        parentFolderId: String,
        relativePath: String,
        local: LocalFileItem
    ) {
        val inputStream = checkNotNull(
            localSafManager.openInputStream(local.uri)
        ) { "Failed to open InputStream for ${local.uri}" }

        val createdRemote = inputStream.use { inStream ->
            driveRepository.uploadFile(
                parentFolderId = parentFolderId,
                name = local.name,
                mimeType = local.mimeType,
                inputStream = inStream
            )
        }

        val localMd5 = local.md5Hash ?: localSafManager.calculateMd5(local.uri)
        val now = System.currentTimeMillis()

        val newBaseline = FileMetadataEntity(
            id = UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = relativePath,
            fileName = local.name,
            remoteFileId = createdRemote.id,
            remoteMd5 = createdRemote.md5Checksum ?: localMd5,
            remoteModifiedTime = createdRemote.modifiedTime,
            localUri = local.uri.toString(),
            localMd5 = localMd5,
            localModifiedTime = local.lastModified,
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(newBaseline)
    }

    private suspend fun updateBaselineSynchronized(
        pairId: String,
        relativePath: String,
        remote: DriveFileItem,
        local: LocalFileItem
    ) {
        val localMd5 = local.md5Hash ?: remote.md5Checksum
        val now = System.currentTimeMillis()
        val baseline = FileMetadataEntity(
            id = UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = relativePath,
            fileName = local.name,
            remoteFileId = remote.id,
            remoteMd5 = remote.md5Checksum ?: localMd5,
            remoteModifiedTime = remote.modifiedTime,
            localUri = local.uri.toString(),
            localMd5 = localMd5,
            localModifiedTime = local.lastModified,
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(baseline)
    }

    private suspend fun resolveRemoteParentFolderId(
        rootFolderId: String,
        relativePath: String,
        folderCache: MutableMap<String, String>
    ): String {
        if (!relativePath.contains("/")) {
            return rootFolderId
        }

        val parentRelativeDir = relativePath.substringBeforeLast("/")
        val cached = folderCache[parentRelativeDir]
        if (cached != null) return cached

        // Recursively build missing remote folders
        val segments = parentRelativeDir.split("/").filter { it.isNotEmpty() }
        var currentFolderId = rootFolderId
        var runningPath = ""

        for (segment in segments) {
            runningPath = if (runningPath.isEmpty()) segment else "$runningPath/$segment"
            val existing = folderCache[runningPath]
            if (existing != null) {
                currentFolderId = existing
            } else {
                val created = driveRepository.createFolder(currentFolderId, segment)
                folderCache[runningPath] = created.id
                currentFolderId = created.id
            }
        }

        return currentFolderId
    }
}
