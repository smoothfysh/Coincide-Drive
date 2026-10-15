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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.data.preferences.SyncPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Custom Light Mode Design Palette matching UI specification
private val BgCanvas = Color(0xFFF4F8FC)
private val TextHeadline = Color(0xFF101D38)
private val TextMuted = Color(0xFF6B7280)
private val CardBorderColor = Color(0xFFE8EEF5)
private val CardInnerItemBg = Color(0xFFF1F5FA)
private val RoyalBlue = Color(0xFF1A68D9)
private val DriveCloudBlue = Color(0xFF1A73E8)
private val SuccessGreen = Color(0xFF16A34A)
private val SuccessGreenBg = Color(0xFFD4F5E1)
private val SuccessGreenText = Color(0xFF15803D)
private val ConflictGreenText = Color(0xFF166534)
private val ConflictBadgeBg = Color(0xFFE6F8EE)
private val ConstraintPillBg = Color(0xFFEEF6FF)
private val HeroGradientStart = Color(0xFFEBF5FE)
private val HeroGradientEnd = Color(0xFFE2EFFF)
private val HeroBorder = Color(0xFFD4E6FA)

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
    var showAllHistory by remember { mutableStateOf(false) }

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
        containerColor = BgCanvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Coincide Drive",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Sync Settings",
                            tint = TextHeadline
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BgCanvas
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Sync Status & Immediate Action Card
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, HeroBorder),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(HeroGradientStart, HeroGradientEnd)
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
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
                            shape = RoundedCornerShape(50),
                            color = if (isSyncing) Color(0xFFDCEBFE) else SuccessGreenBg
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isSyncing) RoyalBlue else SuccessGreen,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSyncing) "Syncing changes..." else "Active & Watching",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSyncing) RoyalBlue else SuccessGreenText
                                )
                            }
                        }

                        Text(
                            text = "Automatic Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF7B8798)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Last Synchronized",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastSyncFormatted,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHeadline
                            )
                        }

                        Button(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalBlue
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(17.dp)
                                    .then(if (isSyncing) Modifier.rotate(spinAngle) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSyncing) "Syncing..." else "Sync Now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Active Sync Pair Card
            if (syncPair != null) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, CardBorderColor),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
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
                                    tint = TextHeadline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Active Sync Pair",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextHeadline
                                )
                            }

                            IconButton(
                                onClick = onNavigateToSetup,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Folder Pair",
                                    tint = TextHeadline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Remote Drive Folder Item
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardInnerItemBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = DriveCloudBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = syncPair.remoteFolderName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextHeadline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = syncPair.accountEmail,
                                        fontSize = 13.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Local SAF Folder Item
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardInnerItemBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = syncPair.localDisplayPath,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextHeadline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Phone Storage (Connected)",
                                        fontSize = 13.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Conflict protection tag
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ConflictBadgeBg
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Conflict Protection: Keep both copies safely",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ConflictGreenText
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state if no sync pair configured
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, CardBorderColor),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Link a Google Drive folder with a local device folder to start 2-way syncing.",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToSetup,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                        ) {
                            Text("Setup Sync Pair", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Execution Constraints Summary Card
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, CardBorderColor),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
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
                            text = "Sync Conditions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )

                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color(0xFFB0BAC9)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Configure", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextHeadline)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ConstraintPillBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (preferences.wifiOnly) "Wi-Fi Only" else "Mobile Data OK",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ConstraintPillBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (preferences.syncIntervalMinutes > 0) "Every ${preferences.syncIntervalMinutes} min" else "Manual only",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Background Sync & Battery Optimization Card
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, CardBorderColor),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
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
                                tint = if (isIgnoringBatteryOptimizations) SuccessGreen else Color(0xFFDC2626),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Background Sync",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHeadline
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isIgnoringBatteryOptimizations) SuccessGreenBg else Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Allowed" else "Restricted",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIgnoringBatteryOptimizations) ConflictGreenText else Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isIgnoringBatteryOptimizations) {
                            "Background sync is allowed. Files will sync automatically on schedule even when the app is closed."
                        } else {
                            "Battery saver may pause background syncing when the app is closed. Tap below to allow syncing seamlessly in the background."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp
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
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalBlue
                            )
                        ) {
                            Text(
                                text = "Allow Background Syncing",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 5. Recent Activity Log Card
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, CardBorderColor),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
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
                            text = "Recent Sync History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )
                        if (recentLogs.size > 5) {
                            TextButton(
                                onClick = { showAllHistory = !showAllHistory },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showAllHistory) "Show less" else "Show all (${recentLogs.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RoyalBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (recentLogs.isEmpty()) {
                        Text(
                            text = "No sync runs recorded yet. Click 'Sync Now' above to test.",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    } else {
                        val displayedLogs = if (showAllHistory) recentLogs else recentLogs.take(5)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            displayedLogs.forEach { log ->
                                val dateStr = formatFriendlySyncTime(log.timestamp)
                                val isSuccess = log.status == SyncLogEntity.STATUS_SUCCESS
                                val folderName = syncPair?.remoteFolderName ?: "Sync Folder"

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = CardInnerItemBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSuccess) SuccessGreenBg else Color(0xFFFEE2E2),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                                        contentDescription = null,
                                                        tint = if (isSuccess) SuccessGreen else Color(0xFFDC2626),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = folderName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextHeadline
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                if (isSuccess) {
                                                    val changeCount = log.filesUploaded + log.filesDownloaded
                                                    val subtext = if (changeCount > 0) {
                                                        "$changeCount files updated • In sync"
                                                    } else {
                                                        "All files up to date"
                                                    }
                                                    Text(
                                                        text = subtext,
                                                        fontSize = 12.sp,
                                                        color = TextMuted
                                                    )
                                                } else {
                                                    Text(
                                                        text = log.errorMessage ?: "Sync incomplete • Will retry",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFDC2626),
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = dateStr,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextHeadline
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isSuccess) SuccessGreenBg else Color(0xFFFEE2E2)
                                            ) {
                                                Text(
                                                    text = if (isSuccess) "Synced" else "Failed",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSuccess) SuccessGreenText else Color(0xFFDC2626),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
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

private fun formatFriendlySyncTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    val isSameYear = now.get(Calendar.YEAR) == time.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - time.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameYear && dayDiff == 0 -> "Today at ${timeFormat.format(Date(timestamp))}"
        isSameYear && dayDiff == 1 -> "Yesterday at ${timeFormat.format(Date(timestamp))}"
        else -> "${dateFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
    }
}
