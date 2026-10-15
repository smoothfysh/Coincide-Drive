package com.coincide.drive.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import java.util.Collections

/**
 * Manages Google Play Services authentication for Google Drive integration.
 * Employs native on-device account picker and zero-friction GoogleSignIn flow
 * requesting minimal required scope: [DriveScopes.DRIVE_FILE].
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        val DRIVE_SCOPE = Scope(DriveScopes.DRIVE)
        val DRIVE_FILE_SCOPE = Scope(DriveScopes.DRIVE_FILE)
        val REQUIRED_SCOPES: Set<String> = setOf(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE)
    }

    private val googleSignInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(DRIVE_SCOPE, DRIVE_FILE_SCOPE)
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    /**
     * Intent to launch the Google Sign-In system dialog.
     */
    val signInIntent: Intent
        get() = signInClient.signInIntent

    /**
     * Returns the currently signed-in Google account, or null if unauthenticated.
     */
    fun getActiveAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Checks if a user is currently signed in and has granted Drive file scope.
     */
    fun hasDrivePermissions(): Boolean {
        val account = getActiveAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)
    }

    /**
     * Checks whether an explicit account has the Drive file scope.
     */
    fun hasDrivePermissions(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)
    }

    /**
     * Builds and returns a [GoogleAccountCredential] for the active Google account.
     * Returns null if no active account is signed in.
     */
    fun getCredential(): GoogleAccountCredential? {
        val account = getActiveAccount() ?: return null
        return getCredentialForAccount(account)
    }

    /**
     * Builds a [GoogleAccountCredential] using OAuth 2.0 for the specified account.
     */
    fun getCredentialForAccount(account: GoogleSignInAccount): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, REQUIRED_SCOPES).apply {
            selectedAccount = account.account
        }
    }

    /**
     * Builds a [GoogleAccountCredential] using OAuth 2.0 for a specified email address.
     */
    fun getCredentialForEmail(email: String): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, REQUIRED_SCOPES).apply {
            selectedAccountName = email
        }
    }

    /**
     * Parses the result from an onActivityResult or ActivityResultLauncher sign-in attempt.
     */
    fun handleSignInResult(task: Task<GoogleSignInAccount>): Result<GoogleSignInAccount> {
        return try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Result.success(account)
            } else {
                Result.failure(IllegalStateException("GoogleSignInAccount was null"))
            }
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts GoogleSignInAccount from activity result intent.
     */
    fun getSignedInAccountFromIntent(data: Intent?): Result<GoogleSignInAccount> {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return handleSignInResult(task)
    }

    /**
     * Signs out the current user session from Google Play Services on this device.
     */
    fun signOut(): Task<Void> {
        return signInClient.signOut()
    }

    /**
     * Revokes granted permissions and unlinks the application.
     */
    fun revokeAccess(): Task<Void> {
        return signInClient.revokeAccess()
    }
}
