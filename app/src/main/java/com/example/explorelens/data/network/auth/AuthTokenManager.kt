package com.example.explorelens.data.network.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.explorelens.data.model.auth.LoginResponse
import com.example.explorelens.data.model.auth.RefreshTokenRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import java.io.IOException
import java.security.GeneralSecurityException

class AuthTokenManager(private val context: Context) {
    private val TAG = "AuthTokenManager"
    private val PREFS_NAME = "encrypted_auth_prefs"
    private val KEY_ACCESS_TOKEN = "access_token"
    private val KEY_REFRESH_TOKEN = "refresh_token"
    private val KEY_USER_ID = "user_id"
    private val KEY_IS_SIGNED_WITH_GOOGLE = "is_signed_with_google"

    private val authApi by lazy { ExploreLensApiClient.authApi }

    private val encryptedSharedPreferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        try {
            val masterKeySpec = KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                .build()

            val masterKey = MasterKey.Builder(context)
                .setKeyGenParameterSpec(masterKeySpec)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences", e)
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: IOException) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences", e)
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveAuthTokens(authResponse: LoginResponse) {
        encryptedSharedPreferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, authResponse.accessToken)
            putString(KEY_REFRESH_TOKEN, authResponse.refreshToken)
            putString(KEY_USER_ID, authResponse._id)
            putBoolean(KEY_IS_SIGNED_WITH_GOOGLE, authResponse.isSignedWithGoogle)
            apply()
        }
        Log.d(TAG, "Auth tokens saved securely")
    }

    fun getAccessToken(): String? {
        return encryptedSharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return encryptedSharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getUserId(): String? {
        return encryptedSharedPreferences.getString(KEY_USER_ID, null)
    }

    fun isSignedWithGoogle(): Boolean {
        return encryptedSharedPreferences.getBoolean(KEY_IS_SIGNED_WITH_GOOGLE, false)
    }

    fun isLoggedIn(): Boolean {
        return getAccessToken() != null && getUserId() != null
    }

    fun clearTokens() {
        encryptedSharedPreferences.edit().clear().apply()
        Log.d(TAG, "Auth tokens cleared")
    }

    // New method to refresh tokens
    suspend fun refreshTokens(): Result<LoginResponse> {
        val refreshToken = getRefreshToken()
        if (refreshToken == null) {
            Log.e(TAG, "No refresh token available")
            return Result.failure(Exception("No refresh token available"))
        }

        return try {
            val request = RefreshTokenRequest(refreshToken)
            val response = authApi.refreshToken(request)

            if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    saveAuthTokens(loginResponse)
                    Log.d(TAG, "Tokens refreshed successfully")
                    Result.success(loginResponse)
                } ?: Result.failure(Exception("Empty response from server"))
            } else {
                Log.e(TAG, "Failed to refresh tokens: ${response.errorBody()?.string()}")
                Result.failure(Exception("Failed to refresh tokens"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing tokens", e)
            Result.failure(e)
        }
    }

    // New method to get valid access token (with auto-refresh)
    suspend fun getValidAccessToken(): String? {
        val currentToken = getAccessToken()
        if (currentToken == null) {
            Log.e(TAG, "No access token available")
            return null
        }

        // Try to refresh the token proactively
        val refreshResult = refreshTokens()
        return if (refreshResult.isSuccess) {
            getAccessToken()
        } else {
            // If refresh fails, return the current token (might still work)
            Log.w(TAG, "Token refresh failed, using current token")
            currentToken
        }
    }

    companion object {
        @Volatile private var INSTANCE: AuthTokenManager? = null

        fun getInstance(context: Context): AuthTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthTokenManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}