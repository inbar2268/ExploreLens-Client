package com.example.explorelens.data.network.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.IOException
import java.security.GeneralSecurityException


class SecureTokenManager(private val context: Context) {

    private val masterKeyAlias by lazy {
        try {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        } catch (e: Exception) {
            Log.e("SecureTokenManager", "Error creating MasterKey", e)
            throw e
        }
    }

    private val sharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                "auth_tokens",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Log.e("SecureTokenManager", "GeneralSecurityException: creating EncryptedSharedPreferences", e)
            context.getSharedPreferences("fallback_tokens", Context.MODE_PRIVATE)
        } catch (e: IOException) {
            Log.e("SecureTokenManager", "IOException: Error creating EncryptedSharedPreferences", e)
            context.getSharedPreferences("fallback_tokens", Context.MODE_PRIVATE)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SecureTokenManager? = null

        fun getInstance(context: Context): SecureTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .putBoolean("isLoggedIn", true)
            .apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString("accessToken", null)
    fun getRefreshToken(): String? = sharedPreferences.getString("refreshToken", null)
    fun isLoggedIn(): Boolean = sharedPreferences.getBoolean("isLoggedIn", false)


    fun updateAccessToken(newAccessToken: String, newRefreshToken: String? = null) {
        sharedPreferences.edit()
            .putString("accessToken", newAccessToken)
            .apply()
        newRefreshToken?.let {
            sharedPreferences.edit()
                .putString("refreshToken", it)
                .apply()
        }
    }

    fun clearTokens() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }

    fun setLoggedIn(loggedIn: Boolean) {
        sharedPreferences.edit()
            .putBoolean("isLoggedIn", loggedIn)
            .apply()
    }

}