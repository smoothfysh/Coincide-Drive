package com.coincide.drive.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coincide.drive.data.entity.SyncPairEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [SyncPairEntity].
 * Manages configuration and persistence for synchronization mappings.
 */
@Dao
interface SyncPairDao {

    @Query("SELECT * FROM sync_pairs ORDER BY created_at DESC")
    fun getAllPairsFlow(): Flow<List<SyncPairEntity>>

    @Query("SELECT * FROM sync_pairs WHERE is_active = 1 ORDER BY created_at DESC")
    fun getActivePairsFlow(): Flow<List<SyncPairEntity>>

    @Query("SELECT * FROM sync_pairs WHERE id = :id LIMIT 1")
    fun getPairByIdFlow(id: String): Flow<SyncPairEntity?>

    @Query("SELECT * FROM sync_pairs ORDER BY created_at DESC")
    suspend fun getAllPairs(): List<SyncPairEntity>

    @Query("SELECT * FROM sync_pairs WHERE is_active = 1 ORDER BY created_at DESC")
    suspend fun getActivePairs(): List<SyncPairEntity>

    @Query("SELECT * FROM sync_pairs WHERE id = :id LIMIT 1")
    suspend fun getPairById(id: String): SyncPairEntity?

    @Query("SELECT * FROM sync_pairs WHERE remote_folder_id = :remoteFolderId AND account_email = :accountEmail LIMIT 1")
    suspend fun getPairByRemoteFolder(remoteFolderId: String, accountEmail: String): SyncPairEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPair(pair: SyncPairEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairs(pairs: List<SyncPairEntity>)

    @Update
    suspend fun updatePair(pair: SyncPairEntity)

    @Query("UPDATE sync_pairs SET is_active = :isActive WHERE id = :id")
    suspend fun setPairActive(id: String, isActive: Boolean)

    @Delete
    suspend fun deletePair(pair: SyncPairEntity)

    @Query("DELETE FROM sync_pairs WHERE id = :id")
    suspend fun deletePairById(id: String)

    @Query("SELECT COUNT(*) FROM sync_pairs")
    suspend fun getPairCount(): Int

    @Query("SELECT COUNT(*) FROM sync_pairs WHERE is_active = 1")
    suspend fun getActivePairCount(): Int
}
