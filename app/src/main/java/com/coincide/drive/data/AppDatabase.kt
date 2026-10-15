package com.coincide.drive.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.coincide.drive.data.dao.FileMetadataDao
import com.coincide.drive.data.dao.SyncLogDao
import com.coincide.drive.data.dao.SyncPairDao
import com.coincide.drive.data.entity.FileMetadataEntity
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity

/**
 * Main Room Database for Coincide Drive.
 * Persists sync pairs, file metadata baselines, and sync execution logs.
 */
@Database(
    entities = [
        SyncPairEntity::class,
        FileMetadataEntity::class,
        SyncLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun syncPairDao(): SyncPairDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        private const val DATABASE_NAME = "coincide_drive.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
