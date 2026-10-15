package com.coincide.drive.data.preferences

/**
 * Data class representing the user sync settings and background constraints.
 */
data class SyncPreferences(
    val wifiOnly: Boolean = true,
    val allowMobileData: Boolean = false,
    val blockRoaming: Boolean = true,
    val onlyWhenCharging: Boolean = false,
    val syncIntervalMinutes: Long = 60L,
    val lastSyncTimestamp: Long = 0L,
    val isSyncInProgress: Boolean = false
) {
    companion object {
        const val INTERVAL_15_MIN = 15L
        const val INTERVAL_30_MIN = 30L
        const val INTERVAL_60_MIN = 60L
        const val INTERVAL_360_MIN = 360L // 6 hours
        const val INTERVAL_1440_MIN = 1440L // Daily / 24 hours
        const val INTERVAL_MANUAL = 0L // Manual Only
    }
}
