package com.coincide.drive.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents the baseline snapshot metadata for an individual file tracked
 * within a synchronization pair. Used for 3-way differential comparisons.
 */
@Entity(
    tableName = "file_metadata",
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
        Index(value = ["pair_id", "relative_path"], unique = true)
    ]
)
data class FileMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "pair_id")
    val pairId: String,

    @ColumnInfo(name = "relative_path")
    val relativePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "remote_file_id")
    val remoteFileId: String? = null,

    @ColumnInfo(name = "remote_md5")
    val remoteMd5: String? = null,

    @ColumnInfo(name = "remote_modified_time")
    val remoteModifiedTime: Long = 0L,

    @ColumnInfo(name = "local_uri")
    val localUri: String? = null,

    @ColumnInfo(name = "local_md5")
    val localMd5: String? = null,

    @ColumnInfo(name = "local_modified_time")
    val localModifiedTime: Long = 0L,

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = STATUS_SYNCED
) {
    companion object {
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_CONFLICT = "CONFLICT"
        const val STATUS_PENDING = "PENDING"
    }
}
