package com.example.explorelens.data.network.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.explorelens.data.model.LoginResponse
import java.io.IOException
import java.security.GeneralSecurityException

class AuthTokenManager(private val context: Context) {
    private val TAG = "AuthTokenManager"
    private val PREFS_NAME = "encrypted_auth_prefs"
    private val KEY_ACCESS_TOKEN = "access_token"
    private val KEY_REFRESH_TOKEN = "refresh_token"
    private val KEY_USER_ID = "user_id"

    private val encryptedSharedPreferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        try {
            // Create a master key for encryption
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

            // Create EncryptedSharedPreferences
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences", e)
            // Fallback to regular SharedPreferences in case of error
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

    fun isLoggedIn(): Boolean {
        return getAccessToken() != null && getUserId() != null
    }

    fun clearTokens() {
        encryptedSharedPreferences.edit().clear().apply()
        Log.d(TAG, "Auth tokens cleared")
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