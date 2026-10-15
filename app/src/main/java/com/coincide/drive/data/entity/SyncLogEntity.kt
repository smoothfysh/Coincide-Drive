package com.coincide.drive.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Audit log recording the execution metrics and outcome of a synchronization run.
 */
@Entity(
    tableName = "sync_logs",
    foreignKeys = [
        ForeignKey(
            entity = SyncPairEntity::class,
            parentColumns = ["id"],
            childColumns = ["pair_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pair_id"]),
        Index(value = ["timestamp"])
    ]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pair_id")
    val pairId: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "files_uploaded")
    val filesUploaded: Int = 0,

    @ColumnInfo(name = "files_downloaded")
    val filesDownloaded: Int = 0,

    @ColumnInfo(name = "conflicts_resolved")
    val conflictsResolved: Int = 0,

    @ColumnInfo(name = "status")
    val status: String = STATUS_SUCCESS,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L
) {
    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
