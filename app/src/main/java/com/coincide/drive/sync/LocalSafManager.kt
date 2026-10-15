package com.coincide.drive.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Representation of a local file within the Storage Access Framework (SAF) tree.
 */
data class LocalFileItem(
    val name: String,
    val relativePath: String,
    val uri: Uri,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val md5Hash: String? = null
)

/**
 * Helper class for interacting with public device storage via the Storage Access Framework (SAF).
 * Handles tree traversal, streaming I/O, file creation, truncation, deletion, and MD5 hash calculation.
 */
class LocalSafManager(private val context: Context) {

    /**
     * Obtains the root [DocumentFile] from a persistent tree URI.
     */
    fun getRootDocument(treeUri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    /**
     * Recursively traverses a SAF document tree and lists all regular files with their relative paths.
     */
    suspend fun listAllFiles(treeUri: Uri, computeHashes: Boolean = true): List<LocalFileItem> =
        withContext(Dispatchers.IO) {
            val root = getRootDocument(treeUri) ?: return@withContext emptyList()
            val result = mutableListOf<LocalFileItem>()
            traverseDirectory(root, "", result, computeHashes)
            result
        }

    private fun traverseDirectory(
        directory: DocumentFile,
        currentRelativePath: String,
        result: MutableList<LocalFileItem>,
        computeHashes: Boolean
    ) {
        val children = directory.listFiles()
        for (child in children) {
            val childName = child.name ?: continue
            val childPath = if (currentRelativePath.isEmpty()) childName else "$currentRelativePath/$childName"

            if (child.isDirectory) {
                traverseDirectory(child, childPath, result, computeHashes)
            } else if (child.isFile) {
                val hash = if (computeHashes) calculateMd5(child.uri) else null
                result.add(
                    LocalFileItem(
                        name = childName,
                        relativePath = childPath,
                        uri = child.uri,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        mimeType = child.type ?: "application/octet-stream",
                        md5Hash = hash
                    )
                )
            }
        }
    }

    /**
     * Resolves a [DocumentFile] corresponding to a relative path from the root tree.
     */
    suspend fun findFileByRelativePath(treeUri: Uri, relativePath: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            val root = getRootDocument(treeUri) ?: return@withContext null
            val segments = relativePath.split("/").filter { it.isNotEmpty() }
            if (segments.isEmpty()) return@withContext null

            var current: DocumentFile = root
            for (i in 0 until segments.size - 1) {
                val dirName = segments[i]
                current = current.findFile(dirName) ?: return@withContext null
                if (!current.isDirectory) return@withContext null
            }

            current.findFile(segments.last())
        }

    /**
     * Ensures directories exist along [relativePath] and creates or gets the target file.
     */
    suspend fun createOrGetFile(
        treeUri: Uri,
        relativePath: String,
        mimeType: String = "application/octet-stream"
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val root = getRootDocument(treeUri) ?: return@withContext null
        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        if (segments.isEmpty()) return@withContext null

        var current: DocumentFile = root
        for (i in 0 until segments.size - 1) {
            val dirName = segments[i]
            var nextDir = current.findFile(dirName)
            if (nextDir == null) {
                nextDir = current.createDirectory(dirName) ?: return@withContext null
            }
            current = nextDir
        }

        val fileName = segments.last()
        val existing = current.findFile(fileName)
        if (existing != null && existing.isFile) {
            existing
        } else {
            current.createFile(mimeType, fileName)
        }
    }

    /**
     * Opens an [InputStream] for reading content from a file URI.
     */
    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Opens an [OutputStream] for writing content into a file URI.
     * Uses "wt" mode to truncate any existing content.
     */
    fun openOutputStream(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri, "wt")
    }

    /**
     * Deletes a file or directory at [uri].
     */
    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = DocumentFile.fromSingleUri(context, uri)
            file?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculates the MD5 checksum of a file given its [Uri].
     * Formats output as 32 lowercase hex characters matching Google Drive checksums.
     */
    fun calculateMd5(uri: Uri): String? {
        return try {
            val inputStream = openInputStream(uri) ?: return null
            calculateMd5(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates MD5 hash for any [InputStream], closing the stream when finished.
     */
    fun calculateMd5(inputStream: InputStream): String {
        return inputStream.use { stream ->
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
