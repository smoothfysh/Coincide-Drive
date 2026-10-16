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
     * Normalizes a filename/directory name to match Android ExternalStorageProvider's FAT filename rules
     * (trims trailing spaces/dots, replaces illegal characters with '_').
     */
    fun sanitizeFatName(name: String): String {
        if (name.isEmpty() || name == "." || name == "..") return "_"
        val sb = StringBuilder(name.length)
        for (c in name) {
            val isIllegal = c.code in 0x00..0x1f ||
                c.code == 0x7f ||
                c == '"' || c == '*' || c == '/' || c == ':' ||
                c == '<' || c == '>' || c == '?' || c == '\\' || c == '|'
            sb.append(if (isIllegal) '_' else c)
        }
        var res = sb.toString().trimEnd(' ', '.')
        if (res.isEmpty()) res = "_"
        return res
    }

    /**
     * Finds a child [DocumentFile] by name with exact match first, falling back to
     * case-insensitive and FAT-sanitized matching (needed because Android shared storage
     * is case-insensitive and strips trailing spaces/illegal FAT characters).
     */
    private fun findChild(
        parent: DocumentFile,
        targetName: String,
        expectDirectory: Boolean? = null
    ): DocumentFile? {
        val exact = parent.findFile(targetName)
        if (exact != null && (expectDirectory == null || exact.isDirectory == expectDirectory)) {
            return exact
        }

        val sanitizedTarget = sanitizeFatName(targetName)
        if (sanitizedTarget != targetName) {
            val exactSanitized = parent.findFile(sanitizedTarget)
            if (exactSanitized != null && (expectDirectory == null || exactSanitized.isDirectory == expectDirectory)) {
                return exactSanitized
            }
        }

        val trimmedTarget = targetName.trim()
        val children = parent.listFiles()
        return children.firstOrNull { child ->
            if (expectDirectory != null && child.isDirectory != expectDirectory) return@firstOrNull false
            val childName = child.name ?: return@firstOrNull false
            childName.equals(targetName, ignoreCase = true) ||
                childName.equals(sanitizedTarget, ignoreCase = true) ||
                childName.trim().equals(trimmedTarget, ignoreCase = true) ||
                sanitizeFatName(childName).equals(sanitizedTarget, ignoreCase = true)
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
                current = findChild(current, dirName, expectDirectory = true) ?: return@withContext null
            }

            findChild(current, segments.last(), expectDirectory = false)
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
            val sanitizedDirName = sanitizeFatName(dirName)
            var nextDir = findChild(current, dirName, expectDirectory = true)
            if (nextDir == null) {
                nextDir = current.createDirectory(dirName)
                    ?: (if (sanitizedDirName != dirName) current.createDirectory(sanitizedDirName) else null)
                    ?: findChild(current, dirName, expectDirectory = true)
                    ?: return@withContext null
            }
            current = nextDir
        }

        val fileName = segments.last()
        val existing = findChild(current, fileName, expectDirectory = false)
        if (existing != null && existing.isFile) {
            return@withContext existing
        }

        val sanitizedFileName = sanitizeFatName(fileName)
        // Use application/octet-stream for markdown or Google Workspace MIME types so Android SAF
        // does not reject the MIME type or append an unwanted extension like .txt
        val safeMimeType = if (
            mimeType.isBlank() ||
            mimeType.contains("markdown", ignoreCase = true) ||
            mimeType.startsWith("application/vnd.google-apps")
        ) {
            "application/octet-stream"
        } else {
            mimeType
        }

        current.createFile(safeMimeType, fileName)
            ?: (if (safeMimeType != "application/octet-stream") current.createFile("application/octet-stream", fileName) else null)
            ?: (if (sanitizedFileName != fileName) current.createFile("application/octet-stream", sanitizedFileName) else null)
            ?: findChild(current, fileName, expectDirectory = false)
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
