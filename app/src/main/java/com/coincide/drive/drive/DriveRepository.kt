package com.coincide.drive.drive

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Representation of a remote Google Drive folder item.
 */
data class DriveFolderItem(
    val id: String,
    val name: String,
    val modifiedTime: Long = 0L,
    val parentId: String? = null,
    val itemCount: Int = 0,
    val children: List<DriveFolderItem> = emptyList()
)

/**
 * Representation of a remote Google Drive regular file item.
 */
data class DriveFileItem(
    val id: String,
    val name: String,
    val md5Checksum: String?,
    val modifiedTime: Long,
    val size: Long,
    val mimeType: String,
    val parentId: String? = null
)

/**
 * Storage quota representation retrieved from Google Drive About endpoint.
 */
data class DriveStorageQuota(
    val usageBytes: Long,
    val limitBytes: Long,
    val usageInDriveBytes: Long,
    val usageInTrashBytes: Long
) {
    val usagePercent: Float
        get() = if (limitBytes > 0) (usageBytes.toFloat() / limitBytes.toFloat()) * 100f else 0f
}

/**
 * Repository wrapping the Google Drive v3 REST API.
 * Performs remote operations including folder browsing, file listing, streaming downloads,
 * uploads, differential updates, trashing, and quota querying.
 */
class DriveRepository(
    private val credential: GoogleAccountCredential,
    private val appName: String = "Coincide Drive"
) {

    val driveService: Drive by lazy {
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(appName).build()
    }

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val DEFAULT_PAGE_SIZE = 100
    }

    /**
     * Lists child folders inside a specified parent folder.
     * If [parentId] is null or "root", queries the Drive root folder.
     */
    suspend fun listFolders(parentId: String? = null): List<DriveFolderItem> = withContext(Dispatchers.IO) {
        val parent = if (parentId.isNullOrBlank()) "root" else parentId
        val query = "mimeType = '$FOLDER_MIME_TYPE' and trashed = false and '$parent' in parents"
        val folders = mutableListOf<DriveFolderItem>()
        var pageToken: String? = null

        do {
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, modifiedTime, parents)")
                .setPageSize(DEFAULT_PAGE_SIZE)
                .setOrderBy("name")
                .setPageToken(pageToken)
                .execute()

            result.files?.forEach { file ->
                folders.add(
                    DriveFolderItem(
                        id = file.id,
                        name = file.name,
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        parentId = file.parents?.firstOrNull() ?: parent
                    )
                )
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)

        folders
    }

    /**
     * Lists all regular files (excluding subfolders) contained within [folderId].
     */
    suspend fun listFiles(folderId: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val query = "mimeType != '$FOLDER_MIME_TYPE' and trashed = false and '$folderId' in parents"
        val fileList = mutableListOf<DriveFileItem>()
        var pageToken: String? = null

        do {
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, md5Checksum, modifiedTime, size, mimeType, parents)")
                .setPageSize(DEFAULT_PAGE_SIZE)
                .setOrderBy("name")
                .setPageToken(pageToken)
                .execute()

            result.files?.forEach { file ->
                fileList.add(
                    DriveFileItem(
                        id = file.id,
                        name = file.name,
                        md5Checksum = file.md5Checksum,
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        size = file.getSize() ?: 0L,
                        mimeType = file.mimeType ?: "application/octet-stream",
                        parentId = file.parents?.firstOrNull() ?: folderId
                    )
                )
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)

        fileList
    }

    /**
     * Retrieves metadata for a single Google Drive file or folder by [fileId].
     */
    suspend fun getFileMetadata(fileId: String): DriveFileItem? = withContext(Dispatchers.IO) {
        try {
            val file = driveService.files().get(fileId)
                .setFields("id, name, md5Checksum, modifiedTime, size, mimeType, parents, trashed")
                .execute()

            if (file.trashed == true) return@withContext null

            DriveFileItem(
                id = file.id,
                name = file.name,
                md5Checksum = file.md5Checksum,
                modifiedTime = file.modifiedTime?.value ?: 0L,
                size = file.getSize() ?: 0L,
                mimeType = file.mimeType ?: "application/octet-stream",
                parentId = file.parents?.firstOrNull()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads the file content from Google Drive and streams it directly to [outputStream].
     */
    suspend fun downloadFile(fileId: String, outputStream: OutputStream): Unit = withContext(Dispatchers.IO) {
        driveService.files().get(fileId)
            .executeMediaAndDownloadTo(outputStream)
        outputStream.flush()
    }

    /**
     * Uploads a new file to Google Drive inside [parentFolderId] with streaming content.
     */
    suspend fun uploadFile(
        parentFolderId: String,
        name: String,
        mimeType: String = "application/octet-stream",
        inputStream: InputStream
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            this.name = name
            this.parents = listOf(parentFolderId)
        }
        val mediaContent = InputStreamContent(mimeType, inputStream)

        val createdFile = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id, name, md5Checksum, modifiedTime, size, mimeType, parents")
            .execute()

        DriveFileItem(
            id = createdFile.id,
            name = createdFile.name,
            md5Checksum = createdFile.md5Checksum,
            modifiedTime = createdFile.modifiedTime?.value ?: System.currentTimeMillis(),
            size = createdFile.getSize() ?: 0L,
            mimeType = createdFile.mimeType ?: mimeType,
            parentId = parentFolderId
        )
    }

    /**
     * Updates an existing file's binary content on Google Drive.
     */
    suspend fun updateFile(
        fileId: String,
        inputStream: InputStream,
        mimeType: String = "application/octet-stream"
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val mediaContent = InputStreamContent(mimeType, inputStream)
        val updatedFile = driveService.files().update(fileId, File(), mediaContent)
            .setFields("id, name, md5Checksum, modifiedTime, size, mimeType, parents")
            .execute()

        DriveFileItem(
            id = updatedFile.id,
            name = updatedFile.name,
            md5Checksum = updatedFile.md5Checksum,
            modifiedTime = updatedFile.modifiedTime?.value ?: System.currentTimeMillis(),
            size = updatedFile.getSize() ?: 0L,
            mimeType = updatedFile.mimeType ?: mimeType,
            parentId = updatedFile.parents?.firstOrNull()
        )
    }

    /**
     * Moves a file to Google Drive trash.
     */
    suspend fun trashFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        val update = File().apply {
            trashed = true
        }
        driveService.files().update(fileId, update).execute()
    }

    /**
     * Permanently deletes a file from Google Drive.
     */
    suspend fun deletePermanently(fileId: String): Unit = withContext(Dispatchers.IO) {
        driveService.files().delete(fileId).execute()
    }

    /**
     * Creates a new folder inside [parentFolderId].
     */
    suspend fun createFolder(parentFolderId: String, folderName: String): DriveFolderItem = withContext(Dispatchers.IO) {
        val folderMetadata = File().apply {
            name = folderName
            mimeType = FOLDER_MIME_TYPE
            parents = listOf(parentFolderId)
        }
        val created = driveService.files().create(folderMetadata)
            .setFields("id, name, modifiedTime, parents")
            .execute()

        DriveFolderItem(
            id = created.id,
            name = created.name,
            modifiedTime = created.modifiedTime?.value ?: System.currentTimeMillis(),
            parentId = parentFolderId
        )
    }

    /**
     * Queries the user's Google Drive storage quota and usage breakdown.
     */
    suspend fun getStorageQuota(): DriveStorageQuota = withContext(Dispatchers.IO) {
        val about = driveService.about().get()
            .setFields("storageQuota")
            .execute()

        val quota = about.storageQuota
        DriveStorageQuota(
            usageBytes = quota?.usage ?: 0L,
            limitBytes = quota?.limit ?: 0L,
            usageInDriveBytes = quota?.usageInDrive ?: 0L,
            usageInTrashBytes = quota?.usageInDriveTrash ?: 0L
        )
    }
}
