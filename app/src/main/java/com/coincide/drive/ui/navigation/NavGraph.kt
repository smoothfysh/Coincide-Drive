package com.coincide.drive.ui.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.coincide.drive.data.AppDatabase
import com.coincide.drive.data.entity.SyncPairEntity
import com.coincide.drive.data.preferences.SyncPreferences
import com.coincide.drive.data.preferences.SyncPreferencesManager
import com.coincide.drive.drive.DriveRepository
import com.coincide.drive.drive.GoogleAuthManager
import com.coincide.drive.ui.account.AccountPickerScreen
import com.coincide.drive.ui.account.AccountPickerUiState
import com.coincide.drive.ui.folder.FolderBrowserScreen
import com.coincide.drive.ui.folder.FolderBrowserUiState
import com.coincide.drive.ui.home.HomeScreen
import com.coincide.drive.ui.settings.SyncInterval
import com.coincide.drive.ui.settings.SyncSettingsScreen
import com.coincide.drive.ui.settings.SyncSettingsUiState
import com.coincide.drive.work.SyncScheduler
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AccountPicker : Screen("account_picker")
    data object FolderBrowser : Screen("folder_browser")
    data object SyncSettings : Screen("sync_settings")
}

@Composable
fun CoincideNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authManager = remember { GoogleAuthManager(context) }
    val database = remember { AppDatabase.getInstance(context) }
    val preferencesManager = remember { SyncPreferencesManager.getInstance(context) }
    val workManager = remember { WorkManager.getInstance(context) }

    // Live observation of Active Pairs, Preferences, and Audit Logs
    val activePairs by database.syncPairDao().getActivePairsFlow().collectAsState(initial = emptyList())
    val recentLogs by database.syncLogDao().getRecentLogsFlow(25).collectAsState(initial = emptyList())
    val preferences by preferencesManager.syncPreferencesFlow.collectAsState(initial = SyncPreferences())

    // Live observation of WorkManager background sync workers to prevent stuck "Syncing..." UI state
    val immediateWorkInfos by workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.IMMEDIATE_WORK_NAME)
        .collectAsState(initial = emptyList())
    val periodicWorkInfos by workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_WORK_NAME)
        .collectAsState(initial = emptyList())

    val isWorkRunning = immediateWorkInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        || periodicWorkInfos.any { it.state == WorkInfo.State.RUNNING }

    val isSyncing = preferences.isSyncInProgress || isWorkRunning

    val formattedLastSync = remember(preferences.lastSyncTimestamp) {
        if (preferences.lastSyncTimestamp > 0) {
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(preferences.lastSyncTimestamp))
        } else {
            "Not yet synced"
        }
    }

    var activeAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var driveRepository by remember { mutableStateOf<DriveRepository?>(null) }

    var accountState by remember { mutableStateOf(AccountPickerUiState()) }
    var folderState by remember { mutableStateOf(FolderBrowserUiState()) }
    var settingsState by remember { mutableStateOf(SyncSettingsUiState()) }

    // Synchronize settings state when preferences change from DataStore
    LaunchedEffect(preferences) {
        val interval = SyncInterval.entries.firstOrNull { it.minutes == preferences.syncIntervalMinutes }
            ?: SyncInterval.ONE_HOUR
        settingsState = settingsState.copy(
            wifiOnly = preferences.wifiOnly,
            allowMobileData = preferences.allowMobileData,
            blockRoaming = preferences.blockRoaming,
            onlyWhenCharging = preferences.onlyWhenCharging,
            selectedInterval = interval,
            lastSyncTimestamp = formattedLastSync
        )
    }

    // Helper to initialize repository and query real storage quota
    fun loadAccountData(account: GoogleSignInAccount) {
        activeAccount = account
        val credential = authManager.getCredentialForAccount(account)
        val repo = DriveRepository(credential)
        driveRepository = repo

        val hasScope = authManager.hasDrivePermissions(account)
        accountState = accountState.copy(
            accountName = account.displayName ?: account.givenName ?: "",
            accountEmail = account.email ?: "",
            profilePhotoUrl = account.photoUrl?.toString(),
            isPlayServicesVerified = true,
            isSignedIn = true,
            isDriveApiGranted = hasScope,
            isLoading = true
        )

        scope.launch {
            try {
                val quota = repo.getStorageQuota()
                accountState = accountState.copy(
                    usedStorageBytes = quota.usageBytes,
                    totalStorageBytes = quota.limitBytes,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.w("CoincideNavGraph", "Quota fetch error: ${e.message}")
                accountState = accountState.copy(isLoading = false)
            }
        }
    }

    // Google Play Services Sign-In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInResult = authManager.getSignedInAccountFromIntent(result.data)
        signInResult.onSuccess { account ->
            loadAccountData(account)
            Toast.makeText(
                context,
                "Signed in as ${account.email}",
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure { error ->
            Log.e("CoincideNavGraph", "Sign-in error", error)
            accountState = accountState.copy(
                isLoading = false,
                errorMessage = error.localizedMessage
            )
            Toast.makeText(
                context,
                "Sign-in failed: ${error.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Function to trigger Google Play Services native account picker
    fun triggerGoogleAccountPicker() {
        accountState = accountState.copy(isLoading = true)
        authManager.signOut().addOnCompleteListener {
            val intent = authManager.signInIntent
            googleSignInLauncher.launch(intent)
        }
    }

    // Fetch existing account & route to HomeScreen if active sync pair exists
    LaunchedEffect(Unit) {
        val existingAccount = authManager.getActiveAccount()
        if (existingAccount != null) {
            loadAccountData(existingAccount)
        }

        val existingPairs = withContext(Dispatchers.IO) {
            database.syncPairDao().getActivePairs()
        }
        if (existingPairs.isNotEmpty()) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.AccountPicker.route) { inclusive = true }
            }
        }
    }

    // Helper to load real Google Drive folders
    fun fetchDriveFolders() {
        val repo = driveRepository
        if (repo == null) {
            folderState = folderState.copy(
                isLoadingFolders = false,
                folderErrorMessage = "Google Drive session not ready. Please verify your account."
            )
            return
        }

        folderState = folderState.copy(isLoadingFolders = true, folderErrorMessage = null)
        scope.launch {
            try {
                val folders = repo.listFolders("root")
                folderState = folderState.copy(
                    remoteFolders = folders,
                    isLoadingFolders = false
                )
            } catch (e: Exception) {
                Log.e("CoincideNavGraph", "Error listing Drive folders", e)
                folderState = folderState.copy(
                    isLoadingFolders = false,
                    folderErrorMessage = e.localizedMessage ?: "Failed to list folders"
                )
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.AccountPicker.route,
        modifier = modifier
    ) {
        // Destination: Home / Sync Dashboard Screen
        composable(Screen.Home.route) {
            HomeScreen(
                syncPair = activePairs.firstOrNull(),
                preferences = preferences,
                recentLogs = recentLogs,
                isSyncing = isSyncing,
                lastSyncFormatted = formattedLastSync,
                onSyncNow = {
                    SyncScheduler.scheduleImmediateSync(context)
                    Toast.makeText(
                        context,
                        "Immediate sync triggered via WorkManager!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.SyncSettings.route)
                },
                onNavigateToSetup = {
                    navController.navigate(Screen.AccountPicker.route)
                }
            )
        }

        // Screen 1: Google Account Picker & Quota Status
        composable(Screen.AccountPicker.route) {
            AccountPickerScreen(
                uiState = accountState,
                onSwitchAccount = {
                    triggerGoogleAccountPicker()
                },
                onContinue = {
                    if (folderState.remoteFolders.isEmpty() && !folderState.isLoadingFolders) {
                        fetchDriveFolders()
                    }
                    navController.navigate(Screen.FolderBrowser.route)
                }
            )
        }

        // Screen 2: Remote Drive Browser & Local Storage Access Framework (SAF)
        composable(Screen.FolderBrowser.route) {
            FolderBrowserScreen(
                uiState = folderState,
                onRemoteFolderSelected = { folderId, folderName ->
                    folderState = folderState.copy(
                        selectedRemoteFolderId = folderId,
                        selectedRemoteFolderName = folderName
                    )
                },
                onLocalFolderSelected = { uri, displayPath ->
                    folderState = folderState.copy(
                        localTreeUri = uri,
                        localDisplayPath = displayPath
                    )
                    accountState = accountState.copy(isSafPermissionGranted = true)
                    Toast.makeText(
                        context,
                        "Local folder selected: $displayPath",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onRefreshFolders = {
                    fetchDriveFolders()
                },
                onCreateFolder = { newFolderName ->
                    val repo = driveRepository
                    if (repo != null) {
                        scope.launch {
                            folderState = folderState.copy(isLoadingFolders = true)
                            try {
                                val created = repo.createFolder("root", newFolderName)
                                val updatedFolders = repo.listFolders("root")
                                folderState = folderState.copy(
                                    remoteFolders = updatedFolders,
                                    selectedRemoteFolderId = created.id,
                                    selectedRemoteFolderName = created.name,
                                    isLoadingFolders = false
                                )
                                Toast.makeText(
                                    context,
                                    "Folder '${created.name}' created in Google Drive!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                folderState = folderState.copy(
                                    isLoadingFolders = false,
                                    folderErrorMessage = "Error creating folder: ${e.localizedMessage}"
                                )
                            }
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
                onContinue = {
                    navController.navigate(Screen.SyncSettings.route)
                }
            )
        }

        // Screen 3: WorkManager Constraints & Sync Execution Settings
        composable(Screen.SyncSettings.route) {
            SyncSettingsScreen(
                uiState = settingsState.copy(
                    isSyncing = isSyncing,
                    lastSyncTimestamp = formattedLastSync
                ),
                onSyncNowClicked = {
                    SyncScheduler.scheduleImmediateSync(context)
                    Toast.makeText(
                        context,
                        "Immediate sync triggered via WorkManager!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onWifiOnlyChanged = { wifiOnly ->
                    settingsState = settingsState.copy(wifiOnly = wifiOnly)
                },
                onAllowMobileDataChanged = { allowMobileData ->
                    settingsState = settingsState.copy(allowMobileData = allowMobileData)
                },
                onBlockRoamingChanged = { blockRoaming ->
                    settingsState = settingsState.copy(blockRoaming = blockRoaming)
                },
                onOnlyWhenChargingChanged = { onlyWhenCharging ->
                    settingsState = settingsState.copy(onlyWhenCharging = onlyWhenCharging)
                },
                onIntervalSelected = { interval ->
                    settingsState = settingsState.copy(selectedInterval = interval)
                },
                onReindexDiagnostics = {
                    Toast.makeText(
                        context,
                        "Re-scanning files...",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onApplySchedule = {
                    val remoteFolderId = folderState.selectedRemoteFolderId
                    val localUri = folderState.localTreeUri
                    val existingPair = activePairs.firstOrNull()

                    // If user is editing existing settings, use existing pair info if not re-selected
                    val targetRemoteFolderId = remoteFolderId ?: existingPair?.remoteFolderId
                    val targetRemoteFolderName = folderState.selectedRemoteFolderName.ifBlank { existingPair?.remoteFolderName ?: "" }
                    val targetLocalUri = localUri?.toString() ?: existingPair?.localTreeUri
                    val targetLocalPath = if (folderState.localDisplayPath.isNotBlank()) folderState.localDisplayPath else existingPair?.localDisplayPath ?: ""
                    val targetEmail = accountState.accountEmail.ifBlank { existingPair?.accountEmail ?: "" }

                    if (targetRemoteFolderId.isNullOrBlank() || targetLocalUri.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            "Please complete folder selection first.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@SyncSettingsScreen
                    }

                    scope.launch {
                        // Persist sync pair into Room Database
                        val pair = SyncPairEntity(
                            id = existingPair?.id ?: java.util.UUID.randomUUID().toString(),
                            accountEmail = targetEmail,
                            remoteFolderId = targetRemoteFolderId,
                            remoteFolderName = targetRemoteFolderName,
                            localTreeUri = targetLocalUri,
                            localDisplayPath = targetLocalPath,
                            syncDirection = SyncPairEntity.SYNC_DIRECTION_BIDIRECTIONAL,
                            isActive = true
                        )

                        withContext(Dispatchers.IO) {
                            database.syncPairDao().insertPair(pair)
                        }

                        // Persist constraints & schedule periodic work
                        val prefs = SyncPreferences(
                            wifiOnly = settingsState.wifiOnly,
                            allowMobileData = settingsState.allowMobileData,
                            blockRoaming = settingsState.blockRoaming,
                            onlyWhenCharging = settingsState.onlyWhenCharging,
                            syncIntervalMinutes = settingsState.selectedInterval.minutes
                        )
                        preferencesManager.updatePreferences(prefs)
                        SyncScheduler.schedulePeriodicSync(context, prefs)
                        SyncScheduler.scheduleImmediateSync(context)

                        Toast.makeText(
                            context,
                            "Sync configuration applied & scheduled!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Navigate directly to Home Screen and clear setup wizard
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.AccountPicker.route) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
