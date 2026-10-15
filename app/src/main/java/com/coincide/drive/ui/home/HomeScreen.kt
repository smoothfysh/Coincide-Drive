package com.coincide.drive.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coincide.drive.R
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.data.preferences.SyncPreferences
import com.coincide.drive.ui.theme.DriveGoogleBlue
import com.coincide.drive.ui.theme.DriveSuccessGreen
import com.coincide.drive.ui.theme.PillShape
import com.coincide.drive.ui.theme.TonalCardShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    syncPair: SyncPairEntity?,
    preferences: SyncPreferences,
    recentLogs: List<SyncLogEntity>,
    isSyncing: Boolean,
    lastSyncFormatted: String,
    onSyncNow: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "homeSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "homeSpinAngle"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Coincide Drive",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sync Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Sync Status & Immediate Action Card
            Card(
                shape = TonalCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
                        Surface(
                            shape = PillShape,
                            color = if (isSyncing) DriveGoogleBlue.copy(alpha = 0.18f) else DriveSuccessGreen.copy(alpha = 0.18f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isSyncing) DriveGoogleBlue else DriveSuccessGreen,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSyncing) "Syncing changes..." else "Active & Watching",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSyncing) DriveGoogleBlue else DriveSuccessGreen
                                )
                            }
                        }

                        Text(
                            text = "WorkManager",
                            style = MaterialTheme.typography.labelSmall,
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
                                text = "Last Synchronized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastSyncFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .then(if (isSyncing) Modifier.rotate(spinAngle) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSyncing) "Syncing..." else "Sync Now",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Active Sync Pair Card
            if (syncPair != null) {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Active Sync Pair",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = onNavigateToSetup,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Folder Pair",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Remote Drive Folder
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = DriveGoogleBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = syncPair.remoteFolderName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = syncPair.accountEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Local SAF Folder
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = DriveSuccessGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = syncPair.localDisplayPath,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Local Storage (SAF Authorized)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Conflict protection tag
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = DriveSuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Conflict Protection: Keep Both (Timestamp suffix)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state if no sync pair configured
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Sync Pair Configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Link a Google Drive folder with a local device folder to start 2-way syncing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToSetup,
                            shape = PillShape
                        ) {
                            Text("Setup Sync Pair")
                        }
                    }
                }
            }

            // 3. Execution Constraints Summary Card
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Active Constraints",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            shape = PillShape
                        ) {
                            Text("Configure", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (preferences.wifiOnly) DriveGoogleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (preferences.wifiOnly) "Wi-Fi Only" else "Mobile Data OK",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (preferences.syncIntervalMinutes > 0) "${preferences.syncIntervalMinutes}m interval" else "Manual only",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // 4. Background Execution & Battery Optimization Card
            Card(
                shape = TonalCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoringBatteryOptimizations) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) DriveSuccessGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Background Execution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = PillShape,
                            color = if (isIgnoringBatteryOptimizations) DriveSuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Unrestricted" else "Battery Optimized",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isIgnoringBatteryOptimizations) DriveSuccessGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "Unrestricted battery permission is active. Android will not kill or throttle your background sync schedule when the app is closed or screen is off."
                        } else {
                            "Android battery saver is currently active for this app. The system may delay or cancel background sync when the app is closed. Tap below to prevent Android from killing the background sync."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isIgnoringBatteryOptimizations) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallbackIntent)
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Unable to launch battery settings", e)
                                    }
                                }
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Allow Unrestricted Background Sync",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. Recent Activity Log Card
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
                    Text(
                        text = "Recent Sync History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (recentLogs.isEmpty()) {
                        Text(
                            text = "No sync runs recorded yet. Click 'Sync Now' above to test.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentLogs.take(5).forEach { log ->
                                val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                    .format(Date(log.timestamp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (log.status == SyncLogEntity.STATUS_SUCCESS) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (log.status == SyncLogEntity.STATUS_SUCCESS) DriveSuccessGreen else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Up: ${log.filesUploaded} | Down: ${log.filesDownloaded} | Conflicts: ${log.conflictsResolved}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${log.durationMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
