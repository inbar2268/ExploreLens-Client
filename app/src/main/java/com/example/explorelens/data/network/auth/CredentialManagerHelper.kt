package com.example.explorelens.utils

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CredentialManagerHelper private constructor(private val context: Context) {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    /**
     * Build a Google Sign-In request with authorized accounts filter
     */
    fun buildGoogleSignInRequest(
        serverClientId: String,
        filterByAuthorizedAccounts: Boolean = true,
        nonce: String? = null,
        autoSelectEnabled: Boolean = true
    ): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(autoSelectEnabled)
            .apply { nonce?.let { setNonce(it) } }
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    /**
     * Get credential manager instance
     */
    fun getCredentialManager(): CredentialManager = credentialManager

    /**
     * Clear all credential states when the user signs out
     */
    suspend fun clearCredentials() = withContext(Dispatchers.IO) {
        try {
            val request = ClearCredentialStateRequest()
            credentialManager.clearCredentialState(request)
            Log.d(TAG, "Successfully cleared credential state")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credential state", e)
        }
    }

    /**
     * Helper to process Google credential response
     */
    fun processGoogleCredential(response: GetCredentialResponse): GoogleIdTokenInfo? {
        val credential = response.credential

        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleIdTokenInfo(
                    idToken = googleIdTokenCredential.idToken,
                    displayName = googleIdTokenCredential.displayName,
                    email = googleIdTokenCredential.id,
                    profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Google ID token", e)
                null
            }
        } else {
            Log.e(TAG, "Unsupported credential type")
            null
        }
    }

    data class GoogleIdTokenInfo(
        val idToken: String,
        val displayName: String?,
        val email: String,
        val profilePictureUri: String?
    )

    companion object {
        private const val TAG = "CredentialManager"

        @Volatile
        private var instance: CredentialManagerHelper? = null

        fun getInstance(context: Context): CredentialManagerHelper {
            return instance ?: synchronized(this) {
                instance ?: CredentialManagerHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}