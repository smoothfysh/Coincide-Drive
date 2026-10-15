package com.coincide.drive.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.coincide.drive.data.entity.FileMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [FileMetadataEntity].
 * Manages baseline file snapshots for differential 2-way synchronization.
 */
@Dao
interface FileMetadataDao {

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId ORDER BY relative_path ASC")
    fun getFilesByPairFlow(pairId: String): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata ORDER BY relative_path ASC")
    fun getAllFilesFlow(): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId AND sync_status = 'CONFLICT' ORDER BY relative_path ASC")
    fun getConflictsFlow(pairId: String): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId ORDER BY relative_path ASC")
    suspend fun getFilesByPair(pairId: String): List<FileMetadataEntity>

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId ORDER BY relative_path ASC")
    suspend fun getByPairId(pairId: String): List<FileMetadataEntity>

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId AND relative_path = :relativePath LIMIT 1")
    suspend fun getFileByRelativePath(pairId: String, relativePath: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId AND remote_file_id = :remoteFileId LIMIT 1")
    suspend fun getFileByRemoteId(pairId: String, remoteFileId: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE pair_id = :pairId AND sync_status = :status")
    suspend fun getFilesByStatus(pairId: String, status: String): List<FileMetadataEntity>

    @Upsert
    suspend fun insertOrUpdate(file: FileMetadataEntity)

    @Upsert
    suspend fun insertOrUpdateAll(files: List<FileMetadataEntity>)

    @Upsert
    suspend fun upsert(file: FileMetadataEntity)

    @Upsert
    suspend fun upsertAll(files: List<FileMetadataEntity>)

    @Delete
    suspend fun delete(file: FileMetadataEntity)

    @Query("DELETE FROM file_metadata WHERE pair_id = :pairId AND relative_path = :relativePath")
    suspend fun deleteByRelativePath(pairId: String, relativePath: String)

    @Query("DELETE FROM file_metadata WHERE pair_id = :pairId")
    suspend fun deleteForPair(pairId: String)

    @Query("SELECT COUNT(*) FROM file_metadata WHERE pair_id = :pairId")
    suspend fun getFileCountByPair(pairId: String): Int

    @Query("SELECT COUNT(*) FROM file_metadata")
    suspend fun getFileCount(): Int

    @Query("DELETE FROM file_metadata")
    suspend fun clearAll()
}
