package com.coincide.drive.sync

import android.net.Uri
import com.coincide.drive.data.dao.FileMetadataDao
import com.coincide.drive.data.entity.FileMetadataEntity
import com.coincide.drive.drive.DriveFileItem
import com.coincide.drive.drive.DriveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Result returned after a conflict has been successfully resolved using "Keep Both".
 */
data class ConflictResolutionResult(
    val masterBaseline: FileMetadataEntity,
    val conflictBaseline: FileMetadataEntity,
    val conflictFileName: String,
    val conflictRelativePath: String
)

/**
 * Lossless "Keep Both" conflict resolution strategy.
 * When a file is modified both locally and on Google Drive since the last sync baseline:
 * 1. Preserves the local file as the primary master (`filename.ext`).
 * 2. Renames the incoming Drive version to `filename_conflict_YYYYMMDD_HHMMSS.ext`.
 * 3. Downloads the incoming Drive content to local SAF storage under the conflict name.
 * 4. Uploads the conflict file to Google Drive under the same parent folder.
 * 5. Updates Google Drive's original file with the local master's content.
 * 6. Records both entries in the database baseline snapshot to guarantee zero data loss.
 */
class KeepBothConflictResolver(
    private val driveRepository: DriveRepository,
    private val localSafManager: LocalSafManager,
    private val fileMetadataDao: FileMetadataDao
) {

    /**
     * Resolves a concurrent modification conflict between local and remote states.
     */
    suspend fun resolveConflict(
        pairId: String,
        treeUri: Uri,
        remoteParentFolderId: String,
        originalRelativePath: String,
        remoteItem: DriveFileItem,
        localItem: LocalFileItem,
        baseline: FileMetadataEntity?
    ): ConflictResolutionResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = originalRelativePath.substringAfterLast("/")
        val parentRelativeDir = if (originalRelativePath.contains("/")) {
            originalRelativePath.substringBeforeLast("/")
        } else {
            ""
        }

        val hasExtension = fileName.contains(".") && !fileName.startsWith(".")
        val nameWithoutExt = if (hasExtension) fileName.substringBeforeLast(".") else fileName
        val ext = if (hasExtension) fileName.substringAfterLast(".") else ""

        val conflictFileName = if (ext.isNotEmpty()) {
            "${nameWithoutExt}_conflict_${timestamp}.${ext}"
        } else {
            "${nameWithoutExt}_conflict_${timestamp}"
        }

        val conflictRelativePath = if (parentRelativeDir.isNotEmpty()) {
            "$parentRelativeDir/$conflictFileName"
        } else {
            conflictFileName
        }

        // 1. Create the local conflict file in SAF
        val conflictDocFile = checkNotNull(
            localSafManager.createOrGetFile(treeUri, conflictRelativePath, localItem.mimeType)
        ) { "Failed to create local SAF conflict file for $conflictRelativePath" }

        // 2. Download incoming Google Drive content into the local conflict file
        val conflictOutputStream = checkNotNull(
            localSafManager.openOutputStream(conflictDocFile.uri)
        ) { "Cannot open OutputStream for conflict file ${conflictDocFile.uri}" }

        conflictOutputStream.use { out ->
            if (remoteItem.size == 0L && !remoteItem.mimeType.startsWith("application/vnd.google-apps")) {
                out.flush()
            } else {
                driveRepository.downloadFile(remoteItem.id, out)
            }
        }

        // 3. Compute MD5 for downloaded conflict file
        val conflictMd5 = localSafManager.calculateMd5(conflictDocFile.uri) ?: remoteItem.md5Checksum

        // 4. Upload conflict file to Google Drive under the same parent folder
        val conflictInputStream = checkNotNull(
            localSafManager.openInputStream(conflictDocFile.uri)
        ) { "Cannot open InputStream for conflict file ${conflictDocFile.uri}" }

        val uploadedConflictDriveItem = conflictInputStream.use { inStream ->
            driveRepository.uploadFile(
                parentFolderId = remoteParentFolderId,
                name = conflictFileName,
                mimeType = localItem.mimeType,
                inputStream = inStream
            )
        }

        // 5. Update remote master with local master's content
        val localMasterInputStream = checkNotNull(
            localSafManager.openInputStream(localItem.uri)
        ) { "Cannot open InputStream for local master file ${localItem.uri}" }

        val updatedRemoteMaster = localMasterInputStream.use { inStream ->
            driveRepository.updateFile(
                fileId = remoteItem.id,
                inputStream = inStream,
                mimeType = localItem.mimeType
            )
        }

        val localMasterMd5 = localSafManager.calculateMd5(localItem.uri)
        val now = System.currentTimeMillis()

        // 6. Record both baselines in Room DB
        val masterBaseline = FileMetadataEntity(
            id = baseline?.id ?: UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = originalRelativePath,
            fileName = fileName,
            remoteFileId = updatedRemoteMaster.id,
            remoteMd5 = updatedRemoteMaster.md5Checksum ?: localMasterMd5,
            remoteModifiedTime = updatedRemoteMaster.modifiedTime,
            localUri = localItem.uri.toString(),
            localMd5 = localMasterMd5,
            localModifiedTime = localItem.lastModified,
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(masterBaseline)

        val conflictBaseline = FileMetadataEntity(
            id = UUID.randomUUID().toString(),
            pairId = pairId,
            relativePath = conflictRelativePath,
            fileName = conflictFileName,
            remoteFileId = uploadedConflictDriveItem.id,
            remoteMd5 = uploadedConflictDriveItem.md5Checksum ?: conflictMd5,
            remoteModifiedTime = uploadedConflictDriveItem.modifiedTime,
            localUri = conflictDocFile.uri.toString(),
            localMd5 = conflictMd5,
            localModifiedTime = conflictDocFile.lastModified(),
            lastSyncTime = now,
            syncStatus = FileMetadataEntity.STATUS_SYNCED
        )
        fileMetadataDao.insertOrUpdate(conflictBaseline)

        ConflictResolutionResult(
            masterBaseline = masterBaseline,
            conflictBaseline = conflictBaseline,
            conflictFileName = conflictFileName,
            conflictRelativePath = conflictRelativePath
        )
    }
}
