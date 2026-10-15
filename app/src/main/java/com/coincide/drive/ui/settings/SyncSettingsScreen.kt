package com.coincide.drive.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coincide.drive.ui.theme.CoincideDriveTheme
import com.coincide.drive.ui.theme.DriveGoogleBlue
import com.coincide.drive.ui.theme.DriveSuccessGreen
import com.coincide.drive.ui.theme.PillShape
import com.coincide.drive.ui.theme.TonalCardShape

/**
 * Periodic Sync Interval choices matching WorkManager execution capabilities.
 */
enum class SyncInterval(val label: String, val minutes: Long) {
    FIFTEEN_MINUTES("15 min", 15),
    THIRTY_MINUTES("30 min", 30),
    ONE_HOUR("1 hour", 60),
    SIX_HOURS("6 hours", 360),
    DAILY("Daily (24h)", 1440),
    MANUAL_ONLY("Manual Only", 0L)
}

/**
 * UI State for Sync Engine Settings & Constraints.
 */
data class SyncSettingsUiState(
    val liveStatus: String = "Ready to Sync",
    val isSyncing: Boolean = false,
    val syncProgressFraction: Float? = null,
    val lastSyncTimestamp: String = "Not yet synced",
    val filesSyncedCount: Int = 0,
    // WorkManager Constraints
    val wifiOnly: Boolean = true,
    val allowMobileData: Boolean = false,
    val blockRoaming: Boolean = true,
    val onlyWhenCharging: Boolean = false,
    // Frequency
    val selectedInterval: SyncInterval = SyncInterval.ONE_HOUR,
    // Room SQLite diagnostics
    val indexedFilesCount: Int = 0,
    val verifiedHashesCount: Int = 0,
    val databaseSizeBytes: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    uiState: SyncSettingsUiState = SyncSettingsUiState(),
    onSyncNowClicked: () -> Unit = {},
    onWifiOnlyChanged: (Boolean) -> Unit = {},
    onAllowMobileDataChanged: (Boolean) -> Unit = {},
    onBlockRoamingChanged: (Boolean) -> Unit = {},
    onOnlyWhenChargingChanged: (Boolean) -> Unit = {},
    onIntervalSelected: (SyncInterval) -> Unit = {},
    onReindexDiagnostics: () -> Unit = {},
    onApplySchedule: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF4F8FC),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sync Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101D38)
                        )
                        Text(
                            text = "Step 3 of 3: Sync Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1A68D9)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF101D38)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF4F8FC)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = Color(0xFFF4F8FC)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = onApplySchedule,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save & Start Syncing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Hero Card: Live Status & "Sync Now" Button
            HeroStatusSyncCard(
                uiState = uiState,
                onSyncNow = onSyncNowClicked
            )

            // 2. WorkManager Constraint Toggles
            WorkManagerConstraintsCard(
                uiState = uiState,
                onWifiOnlyChanged = onWifiOnlyChanged,
                onAllowMobileDataChanged = onAllowMobileDataChanged,
                onBlockRoamingChanged = onBlockRoamingChanged,
                onOnlyWhenChargingChanged = onOnlyWhenChargingChanged
            )

            // 3. Periodic Sync Interval Selector
            PeriodicFrequencyCard(
                selectedInterval = uiState.selectedInterval,
                onSelectInterval = onIntervalSelected
            )

            // 4. Sync Cache & Storage
            RoomDatabaseDiagnosticsCard(
                uiState = uiState,
                onReindex = onReindexDiagnostics
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Hero status card displaying live sync status, timestamp, and animated "Sync Now" button.
 */
@Composable
private fun HeroStatusSyncCard(
    uiState: SyncSettingsUiState,
    onSyncNow: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinTransition")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Card(
        shape = TonalCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Live status chip
                Surface(
                    shape = PillShape,
                    color = if (uiState.isSyncing) DriveGoogleBlue.copy(alpha = 0.18f) else DriveSuccessGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (uiState.isSyncing) DriveGoogleBlue else DriveSuccessGreen,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.liveStatus,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isSyncing) DriveGoogleBlue else DriveSuccessGreen
                        )
                    }
                }

                Text(
                    text = "${uiState.filesSyncedCount} files synced",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last Completed Sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.lastSyncTimestamp,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onSyncNow,
                    enabled = !uiState.isSyncing,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier
                            .size(18.dp)
                            .then(if (uiState.isSyncing) Modifier.rotate(spinAngle) else Modifier)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isSyncing) "Syncing..." else "Sync Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * WorkManager Constraints Card matching Android NetworkType and Battery constraints.
 */
@Composable
private fun WorkManagerConstraintsCard(
    uiState: SyncSettingsUiState,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onAllowMobileDataChanged: (Boolean) -> Unit,
    onBlockRoamingChanged: (Boolean) -> Unit,
    onOnlyWhenChargingChanged: (Boolean) -> Unit
) {
    Card(
        shape = TonalCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sync Conditions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Constraint: Wi-Fi Only
            ConstraintSwitchRow(
                icon = Icons.Default.Wifi,
                title = "Only on Wi-Fi",
                subtitle = "Sync only when connected to a Wi-Fi network",
                checked = uiState.wifiOnly,
                onCheckedChange = { checked ->
                    onWifiOnlyChanged(checked)
                    if (checked) onAllowMobileDataChanged(false)
                }
            )

            // Constraint: Allow Mobile Data
            ConstraintSwitchRow(
                icon = Icons.Default.SignalCellularAlt,
                title = "Use Mobile Data",
                subtitle = "Allow syncing using your cellular data connection",
                checked = uiState.allowMobileData,
                enabled = !uiState.wifiOnly,
                onCheckedChange = onAllowMobileDataChanged
            )

            // Constraint: Block Roaming
            ConstraintSwitchRow(
                icon = Icons.Default.DataSaverOn,
                title = "Don't sync while roaming",
                subtitle = "Prevents data usage while traveling internationally",
                checked = uiState.blockRoaming,
                onCheckedChange = onBlockRoamingChanged
            )

            // Constraint: Only When Charging
            ConstraintSwitchRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "Only while charging",
                subtitle = "Saves battery by syncing only when plugged into power",
                checked = uiState.onlyWhenCharging,
                onCheckedChange = onOnlyWhenChargingChanged
            )
        }
    }
}

@Composable
private fun ConstraintSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * Periodic Sync Frequency interval selector using Material 3 FilterChips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodicFrequencyCard(
    selectedInterval: SyncInterval,
    onSelectInterval: (SyncInterval) -> Unit
) {
    Card(
        shape = TonalCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sync Frequency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Files are checked automatically in the background to keep everything in sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SyncInterval.values().forEach { interval ->
                    val isSelected = interval == selectedInterval
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectInterval(interval) },
                        label = {
                            Text(
                                text = interval.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

/**
 * Diagnostics card showing SQLite database status & re-indexing trigger.
 */
@Composable
private fun RoomDatabaseDiagnosticsCard(
    uiState: SyncSettingsUiState,
    onReindex: () -> Unit
) {
    Card(
        shape = TonalCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sync Cache & Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${uiState.indexedFilesCount} Files Monitored",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "File states cached locally to sync only changed files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onReindex,
                        shape = PillShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Re-scan",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Re-scan",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SyncSettingsScreenPreview() {
    CoincideDriveTheme {
        SyncSettingsScreen()
    }
}
