package com.coincide.drive.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a synchronization mapping between a remote Google Drive folder
 * and a local directory accessed via Android Storage Access Framework (SAF).
 */
@Entity(tableName = "sync_pairs")
data class SyncPairEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "account_email")
    val accountEmail: String,

    @ColumnInfo(name = "remote_folder_id")
    val remoteFolderId: String,

    @ColumnInfo(name = "remote_folder_name")
    val remoteFolderName: String,

    @ColumnInfo(name = "local_tree_uri")
    val localTreeUri: String,

    @ColumnInfo(name = "local_display_path")
    val localDisplayPath: String,

    @ColumnInfo(name = "sync_direction")
    val syncDirection: String = SYNC_DIRECTION_BIDIRECTIONAL,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SYNC_DIRECTION_BIDIRECTIONAL = "BIDIRECTIONAL"
        const val SYNC_DIRECTION_DRIVE_TO_LOCAL = "DRIVE_TO_LOCAL"
        const val SYNC_DIRECTION_LOCAL_TO_DRIVE = "LOCAL_TO_DRIVE"
    }
}
