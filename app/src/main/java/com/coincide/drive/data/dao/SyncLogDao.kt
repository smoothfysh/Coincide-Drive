package com.coincide.drive.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.coincide.drive.data.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert
    suspend fun insert(log: SyncLogEntity): Long

    @Insert
    suspend fun insertLog(log: SyncLogEntity): Long

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC")
    suspend fun getLogsForPair(pairId: String): List<SyncLogEntity>

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC")
    suspend fun getLogsByPair(pairId: String): List<SyncLogEntity>

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLogForPair(pairId: String): SyncLogEntity?

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLogByPair(pairId: String): SyncLogEntity?

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): SyncLogEntity?

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC")
    fun observeLogsForPair(pairId: String): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs WHERE pair_id = :pairId ORDER BY timestamp DESC")
    fun getLogsByPairFlow(pairId: String): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<SyncLogEntity>

    @Query("DELETE FROM sync_logs WHERE pair_id = :pairId")
    suspend fun deleteLogsForPair(pairId: String)

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllLogs()
}
