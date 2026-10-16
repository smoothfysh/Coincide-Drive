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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.coincide.drive.data.entity.SyncLogEntity
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.data.preferences.LicenseManager
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
private val PausedBadgeBg = Color(0xFFF1F5F9)
private val PausedBadgeText = Color(0xFF64748B)

const val MAX_SYNC_PAIRS = LicenseManager.PRO_MAX_SYNC_PAIRS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    syncPairs: List<SyncPairEntity>,
    preferences: SyncPreferences,
    recentLogs: List<SyncLogEntity>,
    isSyncing: Boolean,
    currentSyncingPairId: String? = null,
    currentPairProcessedCount: Int = 0,
    currentPairTotalCount: Int = 0,
    lastSyncFormatted: String,
    defaultAccountEmail: String = "",
    onSyncNow: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddPair: () -> Unit,
    onRedeemUnlockCode: (email: String, code: String) -> Boolean = { _, _ -> false },
    onEditPair: (SyncPairEntity) -> Unit = {},
    onTogglePairActive: (SyncPairEntity, Boolean) -> Unit = { _, _ -> },
    onDeletePair: (SyncPairEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }
    var showAllHistory by remember { mutableStateOf(false) }
    var pairPendingDelete by remember { mutableStateOf<SyncPairEntity?>(null) }
    var selectedLogForDetails by remember { mutableStateOf<SyncLogEntity?>(null) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    val maxAllowedPairs = preferences.maxSyncPairs
    val activePairsCount = remember(syncPairs) { syncPairs.count { it.isActive } }
    val effectiveIsSyncing = isSyncing && activePairsCount > 0
    val pairById = remember(syncPairs) { syncPairs.associateBy { it.id } }
    val pairNameById = remember(syncPairs) { syncPairs.associate { it.id to it.remoteFolderName } }

    if (showUnlockDialog) {
        val initialEmail = remember(syncPairs, defaultAccountEmail, preferences.unlockedEmail) {
            preferences.unlockedEmail.ifBlank {
                syncPairs.firstOrNull()?.accountEmail?.ifBlank { null } ?: defaultAccountEmail
            }
        }
        UnlockProDialog(
            initialEmail = initialEmail,
            onDismiss = { showUnlockDialog = false },
            onVerifyAndUnlock = { email, code ->
                val unlocked = onRedeemUnlockCode(email, code)
                if (unlocked) {
                    showUnlockDialog = false
                }
                unlocked
            }
        )
    }

    if (pairPendingDelete != null) {
        val target = pairPendingDelete!!
        AlertDialog(
            onDismissRequest = { pairPendingDelete = null },
            title = {
                Text(
                    text = "Unlink Folder Pair?",
                    fontWeight = FontWeight.Bold,
                    color = TextHeadline
                )
            },
            text = {
                Text(
                    text = "Stop syncing \"${target.remoteFolderName}\" (${target.accountEmail}) with \"${target.localDisplayPath}\"?\n\nAll existing files in Google Drive and on your phone will remain untouched.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePair(target)
                        pairPendingDelete = null
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Unlink Folder", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pairPendingDelete = null }) {
                    Text("Cancel", color = TextHeadline)
                }
            }
        )
    }

    if (selectedLogForDetails != null) {
        val log = selectedLogForDetails!!
        val pair = pairById[log.pairId]
        val folderName = pair?.remoteFolderName ?: "Sync Folder"
        val isSuccess = log.status == SyncLogEntity.STATUS_SUCCESS
        val fullTimestamp = remember(log.timestamp) {
            SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
        }
        val durationFormatted = remember(log.durationMs) {
            if (log.durationMs >= 1000L) {
                String.format(Locale.US, "%.1fs", log.durationMs / 1000.0)
            } else {
                "${log.durationMs}ms"
            }
        }

        AlertDialog(
            onDismissRequest = { selectedLogForDetails = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSuccess) SuccessGreenBg else Color(0xFFFEE2E2),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSuccess) SuccessGreen else Color(0xFFDC2626),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isSuccess) "Sync Completed" else "Sync Failed",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )
                        Text(
                            text = fullTimestamp,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Folder & Account Info
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardInnerItemBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Folder: $folderName",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHeadline
                            )
                            if (pair != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Account: ${pair.accountEmail}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Local: ${pair.localDisplayPath}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // Metrics summary row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConstraintPillBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "${log.filesUploaded}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = RoyalBlue
                                )
                                Text("Uploaded", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConstraintPillBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "${log.filesDownloaded}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = RoyalBlue
                                )
                                Text("Downloaded", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ConstraintPillBg,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = durationFormatted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = RoyalBlue
                                )
                                Text("Duration", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }

                    // Error details box if failed or has error message
                    if (!isSuccess || !log.errorMessage.isNullOrBlank()) {
                        Text(
                            text = "Failure Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = log.errorMessage?.ifBlank { null }
                                            ?: "Sync stopped before completion. Please verify your internet connection, Google Drive permissions, and phone storage access.",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF991B1B),
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isSuccess && activePairsCount > 0 && !effectiveIsSyncing) {
                    Button(
                        onClick = {
                            selectedLogForDetails = null
                            onSyncNow()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                    ) {
                        Text("Retry Sync Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { selectedLogForDetails = null },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isSuccess && activePairsCount > 0 && !effectiveIsSyncing) {
                    TextButton(onClick = { selectedLogForDetails = null }) {
                        Text("Close", color = TextHeadline)
                    }
                }
            }
        )
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
                        val isAllPaused = syncPairs.isNotEmpty() && activePairsCount == 0
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when {
                                effectiveIsSyncing -> Color(0xFFDCEBFE)
                                isAllPaused -> PausedBadgeBg
                                else -> SuccessGreenBg
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            when {
                                                effectiveIsSyncing -> RoyalBlue
                                                isAllPaused -> PausedBadgeText
                                                else -> SuccessGreen
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        effectiveIsSyncing -> "Syncing changes..."
                                        isAllPaused -> "All Folders Paused"
                                        syncPairs.size > 1 -> "Active ($activePairsCount of ${syncPairs.size} folders)"
                                        else -> "Active & Watching"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        effectiveIsSyncing -> RoyalBlue
                                        isAllPaused -> PausedBadgeText
                                        else -> SuccessGreenText
                                    }
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
                            enabled = !effectiveIsSyncing && activePairsCount > 0,
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
                                    .then(if (effectiveIsSyncing) Modifier.rotate(spinAngle) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (effectiveIsSyncing) "Syncing..." else "Sync Now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Synced Folders Header + List (1 Folder Free / 5 Folders Unlocked)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                        text = "Synced Folders (${syncPairs.size} / $maxAllowedPairs)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHeadline
                    )
                }

                when {
                    syncPairs.size < maxAllowedPairs -> {
                        Button(
                            onClick = onAddPair,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Folder Pair",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Folder",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    !preferences.isProUnlocked -> {
                        Button(
                            onClick = { showUnlockDialog = true },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Unlock up to 5 Folders",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "+ Add Folder",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    else -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ConstraintPillBg
                        ) {
                            Text(
                                text = "Max 5 Folders",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoyalBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            if (syncPairs.isNotEmpty()) {
                syncPairs.forEachIndexed { index, pair ->
                    val isThisPairSyncing = pair.isActive && effectiveIsSyncing &&
                        (currentSyncingPairId == pair.id || (currentSyncingPairId == null && activePairsCount == 1))
                    val syncingBadgeText = if (currentPairTotalCount > 0) {
                        "Syncing $currentPairProcessedCount/$currentPairTotalCount"
                    } else {
                        "Syncing..."
                    }

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isThisPairSyncing) RoyalBlue else CardBorderColor
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (pair.isActive) 1f else 0.72f)
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
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = when {
                                            isThisPairSyncing -> Color(0xFFDCEBFE)
                                            pair.isActive -> SuccessGreenBg
                                            else -> PausedBadgeBg
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isThisPairSyncing -> Icons.Default.Sync
                                                    pair.isActive -> Icons.Default.PlayCircleOutline
                                                    else -> Icons.Default.PauseCircleOutline
                                                },
                                                contentDescription = null,
                                                tint = when {
                                                    isThisPairSyncing -> RoyalBlue
                                                    pair.isActive -> SuccessGreenText
                                                    else -> PausedBadgeText
                                                },
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .then(if (isThisPairSyncing) Modifier.rotate(spinAngle) else Modifier)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = when {
                                                    isThisPairSyncing -> syncingBadgeText
                                                    pair.isActive -> "Folder #${index + 1} • Active"
                                                    else -> "Folder #${index + 1} • Paused"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isThisPairSyncing -> RoyalBlue
                                                    pair.isActive -> SuccessGreenText
                                                    else -> PausedBadgeText
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = pair.isActive,
                                        onCheckedChange = { active ->
                                            onTogglePairActive(pair, active)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = SuccessGreen
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onEditPair(pair) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Folder Pair",
                                            tint = TextHeadline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { pairPendingDelete = pair },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Unlink Folder Pair",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

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
                                            text = pair.remoteFolderName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextHeadline
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = pair.accountEmail,
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
                                            text = pair.localDisplayPath,
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
                            text = "No Sync Pairs Configured",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextHeadline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Link up to 5 Google Drive folders with local phone folders to start 2-way syncing.",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddPair,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                        ) {
                            Text("Add First Folder Pair", fontWeight = FontWeight.Bold)
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
                                val folderName = pairNameById[log.pairId] ?: "Sync Folder"

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = CardInnerItemBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedLogForDetails = log }
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
                                                        text = log.errorMessage ?: "Sync incomplete • Tap for details",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFDC2626),
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "Tap to view failure details",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = RoyalBlue
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

@Composable
fun UnlockProDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onVerifyAndUnlock: (email: String, code: String) -> Boolean
) {
    var emailInput by remember(initialEmail) { mutableStateOf(initialEmail) }
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ConstraintPillBg,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = RoyalBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Unlock 5 Synced Folders",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHeadline
                    )
                    Text(
                        text = "Cadence-X Unlock Code",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "The standard version syncs 1 folder pair. Enter the email from your purchase invoice and your CADX-YYYY-YYYY unlock code to sync up to 5 folders.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        errorMessage = null
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase(Locale.ROOT)
                        errorMessage = null
                    },
                    label = { Text("Unlock Code") },
                    placeholder = { Text("CADX-YYYY-YYYY") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (emailInput.isBlank()) {
                        errorMessage = "Please enter the email address associated with your unlock code."
                        return@Button
                    }
                    if (codeInput.isBlank()) {
                        errorMessage = "Please enter your CADX-YYYY-YYYY unlock code."
                        return@Button
                    }
                    val success = onVerifyAndUnlock(emailInput.trim(), codeInput.trim())
                    if (!success) {
                        errorMessage = "Invalid unlock code for this email. Please check your CADX-YYYY-YYYY code and email."
                    }
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Unlock 5 Folders", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextHeadline)
            }
        }
    )
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
