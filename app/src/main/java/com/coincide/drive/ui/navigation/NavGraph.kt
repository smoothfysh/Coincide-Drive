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
import com.coincide.drive.data.preferences.LicenseManager
import com.coincide.drive.data.preferences.SyncPreferences
import com.coincide.drive.data.preferences.SyncPreferencesManager
import com.coincide.drive.drive.DriveRepository
import com.coincide.drive.drive.GoogleAuthManager
import com.coincide.drive.ui.account.AccountPickerScreen
import com.coincide.drive.ui.account.AccountPickerUiState
import com.coincide.drive.ui.folder.FolderBrowserScreen
import com.coincide.drive.ui.folder.FolderBrowserUiState
import com.coincide.drive.ui.home.HomeScreen
import com.coincide.drive.ui.home.MAX_SYNC_PAIRS
import com.coincide.drive.ui.settings.SyncInterval
import com.coincide.drive.ui.settings.SyncSettingsScreen
import com.coincide.drive.ui.settings.SyncSettingsUiState
import com.coincide.drive.work.DriveSyncWorker
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

    // Live observation of All Pairs (active + paused), Tracked Files, Preferences, and Audit Logs
    val rawPairs by database.syncPairDao().getAllPairsFlow().collectAsState(initial = emptyList())
    val allPairs = remember(rawPairs) { rawPairs.sortedBy { it.createdAt } }
    val allTrackedFiles by database.fileMetadataDao().getAllFilesFlow().collectAsState(initial = emptyList())
    val recentLogs by database.syncLogDao().getRecentLogsFlow(25).collectAsState(initial = emptyList())
    val preferences by preferencesManager.syncPreferencesFlow.collectAsState(initial = SyncPreferences())

    // Live observation of WorkManager background sync workers and in-process live progress
    val immediateWorkInfos by workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.IMMEDIATE_WORK_NAME)
        .collectAsState(initial = emptyList())
    val periodicWorkInfos by workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_WORK_NAME)
        .collectAsState(initial = emptyList())
    val liveProgress by DriveSyncWorker.liveProgressFlow.collectAsState()

    val isWorkRunning = immediateWorkInfos.any { it.state == WorkInfo.State.RUNNING }
        || periodicWorkInfos.any { it.state == WorkInfo.State.RUNNING }

    val runningWorkInfo = immediateWorkInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        ?: periodicWorkInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }

    val currentSyncingPairId = liveProgress?.pairId
        ?: runningWorkInfo?.progress?.getString(DriveSyncWorker.KEY_CURRENT_PAIR_ID)
    val currentPairProcessedCount = liveProgress?.processedCount
        ?: runningWorkInfo?.progress?.getInt(DriveSyncWorker.KEY_PROCESSED_COUNT, 0) ?: 0
    val currentPairTotalCount = liveProgress?.totalCount
        ?: runningWorkInfo?.progress?.getInt(DriveSyncWorker.KEY_TOTAL_COUNT, 0) ?: 0

    val hasAnyActivePair = remember(allPairs) { allPairs.any { it.isActive } }
    val isSyncing = hasAnyActivePair && (liveProgress != null || isWorkRunning)

    // Reconcile stale isSyncInProgress preference if no worker is actively running
    LaunchedEffect(isWorkRunning, liveProgress, preferences.isSyncInProgress) {
        if (preferences.isSyncInProgress && !isWorkRunning && liveProgress == null) {
            preferencesManager.setSyncInProgress(false)
        }
    }

    val formattedLastSync = remember(preferences.lastSyncTimestamp) {
        if (preferences.lastSyncTimestamp > 0) {
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(preferences.lastSyncTimestamp))
        } else {
            "Not yet synced"
        }
    }

    var activeAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var driveRepository by remember { mutableStateOf<DriveRepository?>(null) }

    // Tracks whether the setup wizard is creating a new pair (null) or editing an existing pair
    var editingPairId by remember { mutableStateOf<String?>(null) }

    var accountState by remember { mutableStateOf(AccountPickerUiState()) }
    var folderState by remember { mutableStateOf(FolderBrowserUiState()) }
    var settingsState by remember { mutableStateOf(SyncSettingsUiState()) }

    // Synchronize settings state when preferences or tracked files change
    LaunchedEffect(preferences, allTrackedFiles.size, allPairs, accountState.accountEmail) {
        val interval = SyncInterval.entries.firstOrNull { it.minutes == preferences.syncIntervalMinutes }
            ?: SyncInterval.ONE_HOUR
        val defaultEmail = allPairs.firstOrNull()?.accountEmail?.ifBlank { null }
            ?: accountState.accountEmail
        settingsState = settingsState.copy(
            wifiOnly = preferences.wifiOnly,
            allowMobileData = preferences.allowMobileData,
            blockRoaming = preferences.blockRoaming,
            onlyWhenCharging = preferences.onlyWhenCharging,
            selectedInterval = interval,
            lastSyncTimestamp = formattedLastSync,
            filesSyncedCount = allTrackedFiles.size,
            indexedFilesCount = allTrackedFiles.size,
            verifiedHashesCount = allTrackedFiles.size,
            isProUnlocked = preferences.isProUnlocked,
            unlockedEmail = preferences.unlockedEmail,
            defaultAccountEmail = defaultEmail
        )
    }

    // Helper to initialize repository and query real storage quota for a GoogleSignInAccount
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
            isLoading = true,
            errorMessage = null
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

    // Helper to load account & DriveRepository by email (for per-pair Google accounts)
    fun loadAccountByEmail(email: String) {
        val currentSignedIn = authManager.getActiveAccount()
        if (currentSignedIn != null && currentSignedIn.email.equals(email, ignoreCase = true)) {
            loadAccountData(currentSignedIn)
            return
        }

        val credential = authManager.getCredentialForEmail(email)
        val repo = DriveRepository(credential)
        driveRepository = repo

        accountState = accountState.copy(
            accountName = email.substringBefore("@"),
            accountEmail = email,
            profilePhotoUrl = null,
            isPlayServicesVerified = true,
            isSignedIn = true,
            isDriveApiGranted = true,
            isLoading = true,
            errorMessage = null
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
                Log.w("CoincideNavGraph", "Quota fetch error for $email: ${e.message}")
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
            // Clear remote folder selection if account changed so user picks from the new account's Drive
            folderState = folderState.copy(
                remoteFolders = emptyList(),
                selectedRemoteFolderId = null,
                selectedRemoteFolderName = ""
            )
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

    // Fetch existing account & route to HomeScreen if any sync pair exists
    LaunchedEffect(Unit) {
        val existingAccount = authManager.getActiveAccount()
        if (existingAccount != null) {
            loadAccountData(existingAccount)
        }

        val existingPairs = withContext(Dispatchers.IO) {
            database.syncPairDao().getAllPairs()
        }
        if (existingPairs.isNotEmpty()) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.AccountPicker.route) { inclusive = true }
            }
        }
    }

    // Helper to load real Google Drive folders for the currently selected account
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

    // Validates and persists the currently configured folder pair
    suspend fun validateAndSavePair(): Boolean {
        val remoteFolderId = folderState.selectedRemoteFolderId
        val remoteFolderName = folderState.selectedRemoteFolderName
        val localUri = folderState.localTreeUri?.toString()
        val localPath = folderState.localDisplayPath
        val targetEmail = accountState.accountEmail

        if (remoteFolderId.isNullOrBlank() || localUri.isNullOrBlank() || targetEmail.isBlank()) {
            Toast.makeText(
                context,
                "Please select both a Google Drive folder and a phone folder.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        val maxAllowedPairs = preferences.maxSyncPairs
        val existingEditingPair = allPairs.firstOrNull { it.id == editingPairId }
        if (existingEditingPair == null && allPairs.size >= maxAllowedPairs) {
            Toast.makeText(
                context,
                "Maximum of $maxAllowedPairs folder pair(s) reached.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // Prevent duplicate Drive folder or local folder across other pairs
        val otherPairs = allPairs.filter { it.id != editingPairId }
        val duplicateRemote = otherPairs.firstOrNull {
            it.remoteFolderId == remoteFolderId && it.accountEmail.equals(targetEmail, ignoreCase = true)
        }
        if (duplicateRemote != null) {
            Toast.makeText(
                context,
                "Drive folder '${duplicateRemote.remoteFolderName}' is already synced in another slot.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val duplicateLocal = otherPairs.firstOrNull { it.localTreeUri == localUri }
        if (duplicateLocal != null) {
            Toast.makeText(
                context,
                "Phone folder '${duplicateLocal.localDisplayPath}' is already linked to '${duplicateLocal.remoteFolderName}'.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val pair = SyncPairEntity(
            id = existingEditingPair?.id ?: java.util.UUID.randomUUID().toString(),
            accountEmail = targetEmail,
            remoteFolderId = remoteFolderId,
            remoteFolderName = remoteFolderName,
            localTreeUri = localUri,
            localDisplayPath = localPath,
            syncDirection = SyncPairEntity.SYNC_DIRECTION_BIDIRECTIONAL,
            isActive = existingEditingPair?.isActive ?: true,
            createdAt = existingEditingPair?.createdAt ?: System.currentTimeMillis()
        )

        withContext(Dispatchers.IO) {
            database.syncPairDao().insertPair(pair)
        }
        return true
    }

    // Verifies a CADX-YYYY-YYYY unlock code and persists Pro unlock state if valid
    fun redeemUnlockCode(email: String, code: String): Boolean {
        val candidateEmails = buildList {
            allPairs.forEach { if (it.accountEmail.isNotBlank()) add(it.accountEmail) }
            if (accountState.accountEmail.isNotBlank()) add(accountState.accountEmail)
            activeAccount?.email?.let { if (it.isNotBlank()) add(it) }
        }
        val matchedEmail = LicenseManager.verifyCode(
            inputCode = code,
            primaryEmail = email,
            candidateEmails = candidateEmails
        )
        return if (matchedEmail != null) {
            val formattedCode = LicenseManager.generateCodeForEmail(matchedEmail)
            scope.launch {
                preferencesManager.setProUnlocked(
                    unlocked = true,
                    email = matchedEmail,
                    code = formattedCode
                )
            }
            Toast.makeText(
                context,
                "Unlocked! You can now sync up to ${LicenseManager.PRO_MAX_SYNC_PAIRS} folders.",
                Toast.LENGTH_LONG
            ).show()
            true
        } else {
            false
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
                syncPairs = allPairs,
                preferences = preferences,
                recentLogs = recentLogs,
                isSyncing = isSyncing,
                currentSyncingPairId = currentSyncingPairId,
                currentPairProcessedCount = currentPairProcessedCount,
                currentPairTotalCount = currentPairTotalCount,
                lastSyncFormatted = formattedLastSync,
                defaultAccountEmail = accountState.accountEmail,
                onSyncNow = {
                    SyncScheduler.scheduleImmediateSync(context)
                    Toast.makeText(
                        context,
                        "Syncing all active folder pairs...",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onNavigateToSettings = {
                    // Clear wizard folder state when opening global settings directly from Home
                    editingPairId = null
                    folderState = FolderBrowserUiState()
                    navController.navigate(Screen.SyncSettings.route)
                },
                onAddPair = {
                    val maxAllowedPairs = preferences.maxSyncPairs
                    if (allPairs.size >= maxAllowedPairs) {
                        Toast.makeText(
                            context,
                            "You can sync up to $maxAllowedPairs folder(s) at a time.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@HomeScreen
                    }
                    editingPairId = null
                    folderState = FolderBrowserUiState()
                    val currentAccount = authManager.getActiveAccount()
                    if (currentAccount != null) {
                        loadAccountData(currentAccount)
                    }
                    navController.navigate(Screen.AccountPicker.route)
                },
                onRedeemUnlockCode = { email, code ->
                    redeemUnlockCode(email, code)
                },
                onEditPair = { pair ->
                    editingPairId = pair.id
                    folderState = FolderBrowserUiState(
                        selectedRemoteFolderId = pair.remoteFolderId,
                        selectedRemoteFolderName = pair.remoteFolderName,
                        localTreeUri = Uri.parse(pair.localTreeUri),
                        localDisplayPath = pair.localDisplayPath
                    )
                    accountState = accountState.copy(isSafPermissionGranted = true)
                    if (pair.accountEmail.isNotBlank()) {
                        loadAccountByEmail(pair.accountEmail)
                    }
                    navController.navigate(Screen.AccountPicker.route)
                },
                onTogglePairActive = { pair, active ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            database.syncPairDao().setPairActive(pair.id, active)
                        }
                        if (!active) {
                            val remainingActive = allPairs.count { it.id != pair.id && it.isActive }
                            if (currentSyncingPairId == pair.id || remainingActive == 0) {
                                workManager.cancelUniqueWork(SyncScheduler.IMMEDIATE_WORK_NAME)
                                DriveSyncWorker.clearLiveProgress()
                                preferencesManager.setSyncInProgress(false)
                            }
                        }
                        val statusMsg = if (active) {
                            "Resumed syncing '${pair.remoteFolderName}'"
                        } else {
                            "Paused syncing '${pair.remoteFolderName}'"
                        }
                        Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeletePair = { pair ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            database.syncPairDao().deletePairById(pair.id)
                        }
                        val remainingActive = allPairs.count { it.id != pair.id && it.isActive }
                        if (currentSyncingPairId == pair.id || remainingActive == 0) {
                            workManager.cancelUniqueWork(SyncScheduler.IMMEDIATE_WORK_NAME)
                            DriveSyncWorker.clearLiveProgress()
                            preferencesManager.setSyncInProgress(false)
                        }
                        Toast.makeText(
                            context,
                            "Unlinked '${pair.remoteFolderName}'",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Screen 1: Google Account Picker & Quota Status
        composable(Screen.AccountPicker.route) {
            val isEditing = editingPairId != null
            val hasExistingPairs = allPairs.isNotEmpty()
            val stepSubtitle = when {
                isEditing -> "Edit Folder Pair • Step 1 of 2: Google Account"
                hasExistingPairs -> "Folder Pair #${(allPairs.size + 1).coerceAtMost(MAX_SYNC_PAIRS)} • Step 1 of 2: Connect Account"
                else -> "Step 1 of 3: Connect Account"
            }

            AccountPickerScreen(
                uiState = accountState,
                stepSubtitle = stepSubtitle,
                onSwitchAccount = {
                    triggerGoogleAccountPicker()
                },
                onContinue = {
                    fetchDriveFolders()
                    navController.navigate(Screen.FolderBrowser.route)
                },
                onBack = if (hasExistingPairs) {
                    { navController.popBackStack() }
                } else null
            )
        }

        // Screen 2: Remote Drive Browser & Local Storage Access Framework (SAF)
        composable(Screen.FolderBrowser.route) {
            val isEditing = editingPairId != null
            val hasExistingPairs = allPairs.isNotEmpty()
            val subtitleText = when {
                isEditing -> "Edit Folder Pair • Step 2 of 2: Choose Folders"
                hasExistingPairs -> "Folder Pair #${(allPairs.size + 1).coerceAtMost(MAX_SYNC_PAIRS)} • Step 2 of 2: Choose Folders"
                else -> "Step 2 of 3: Choose Folders to Sync"
            }
            val continueButtonText = when {
                isEditing -> "Update Folder Pair"
                hasExistingPairs -> "Save Folder Pair"
                else -> "Save & Continue to Settings"
            }

            FolderBrowserScreen(
                uiState = folderState,
                subtitleText = subtitleText,
                continueButtonText = continueButtonText,
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
                    if (hasExistingPairs || isEditing) {
                        scope.launch {
                            val saved = validateAndSavePair()
                            if (saved) {
                                editingPairId = null
                                folderState = FolderBrowserUiState()
                                SyncScheduler.scheduleImmediateSync(context)
                                Toast.makeText(
                                    context,
                                    if (isEditing) "Folder pair updated & syncing!" else "Folder pair added & syncing!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        }
                    } else {
                        navController.navigate(Screen.SyncSettings.route)
                    }
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
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            database.fileMetadataDao().clearAll()
                        }
                        SyncScheduler.scheduleImmediateSync(context)
                        Toast.makeText(
                            context,
                            "Sync cache cleared. Re-scanning all folders...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onRedeemUnlockCode = { email, code ->
                    redeemUnlockCode(email, code)
                },
                onApplySchedule = {
                    scope.launch {
                        val hasPendingFolderSelection = !folderState.selectedRemoteFolderId.isNullOrBlank() &&
                            folderState.localTreeUri != null

                        if (hasPendingFolderSelection) {
                            val saved = validateAndSavePair()
                            if (!saved) return@launch
                            editingPairId = null
                            folderState = FolderBrowserUiState()
                        } else if (allPairs.isEmpty()) {
                            Toast.makeText(
                                context,
                                "Please complete folder selection first.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        // Persist constraints & schedule periodic work
                        val prefs = SyncPreferences(
                            wifiOnly = settingsState.wifiOnly,
                            allowMobileData = settingsState.allowMobileData,
                            blockRoaming = settingsState.blockRoaming,
                            onlyWhenCharging = settingsState.onlyWhenCharging,
                            syncIntervalMinutes = settingsState.selectedInterval.minutes,
                            lastSyncTimestamp = preferences.lastSyncTimestamp,
                            isSyncInProgress = preferences.isSyncInProgress
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
                            popUpTo(0) { inclusive = true }
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
