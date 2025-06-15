package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.ar.AppRenderer.Companion.TAG
import com.example.explorelens.data.model.ChangePasswordRequest
import com.example.explorelens.data.model.ForgotPasswordRequest
import com.example.explorelens.data.model.GoogleSignInRequest
import com.example.explorelens.data.model.LoginRequest
import com.example.explorelens.data.model.LoginResponse
import com.example.explorelens.data.model.LogoutRequest
import com.example.explorelens.data.model.RegisterRequest
import com.example.explorelens.data.model.ResetPasswordRequest
import com.example.explorelens.data.network.auth.AuthApi
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.network.ExploreLensApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthRepository(private val context: Context) {

    private val authApi: AuthApi = ExploreLensApiClient.authApi
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    suspend fun registerUser(name: String, email: String, password: String): Result<LoginResponse> {
        val registerRequest = RegisterRequest(name, email, password, "")
        return try {
            val response = authApi.register(registerRequest)
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveAuthTokens(it)
                    Result.success(it)
                } ?: Result.failure(Exception("Registration failed: Empty response body"))
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    private fun handleNetworkError(e: Exception): String {
        val errorMessage = when {
            e is java.net.ConnectException ||
                    e is java.net.SocketTimeoutException ||
                    e.message?.contains("Failed to connect") == true -> "Network error"
            else -> e.message ?: "Unknown error"
        }
        return errorMessage
    }

    private fun parseErrorMessage(response: Response<*>): String {
        val errorBody = response.errorBody()?.string()
        return try {
            val json = org.json.JSONObject(errorBody ?: "")
            json.optString("message", "Unknown error occurred")
        } catch (e: Exception) {
            errorBody ?: "Unknown error occurred"
        }
    }

    suspend fun loginUser(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveAuthTokens(it)
                    Result.success(it)
                } ?: Result.failure(Exception("Login failed: Empty response body"))
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun googleSignIn(idToken: String): Result<LoginResponse> {
        val googleAuthRequest = GoogleSignInRequest(idToken)
        return try {
            val response = authApi.googleSignIn(googleAuthRequest)
            if (response.isSuccessful) {
                response.body()?.let {
                    tokenManager.saveAuthTokens(it)
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response from server"))
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun forgotPassword(email: String): Result<Unit> {
        val forgotPasswordRequest = ForgotPasswordRequest(email)
        return try {
            val response = authApi.forgotPassword(forgotPasswordRequest)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        val resetPasswordRequest = ResetPasswordRequest(token, newPassword)
        return try {
            val response = authApi.resetPassword(resetPasswordRequest)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    suspend fun logout(): Result<Unit> {
        val refreshToken = tokenManager.getRefreshToken()
        deleteTokens()
        return if (refreshToken != null) {
            try {
                val request = LogoutRequest(refreshToken)
                val response = authApi.logout(request)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(parseErrorMessage(response)))
                }
            } catch (e: Exception) {
                val errorMessage = handleNetworkError(e)
                Result.failure(Exception(errorMessage))
            }
        } else {
            Result.failure(Exception("No refresh token found"))
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            // Get a valid access token (will refresh if needed)
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "No valid access token available")
                return Result.failure(Exception("Authentication failed. Please log in again."))
            }

            val request = ChangePasswordRequest(currentPassword, newPassword)
            val authHeader = "Bearer $accessToken"
            val response = authApi.changePassword(authHeader, request)

            if (response.isSuccessful) {
                Log.d(TAG, "Password changed successfully")
                Result.success(Unit)
            } else if (response.code() == 401) {
                // Token might still be expired, try to refresh once more
                Log.w(TAG, "Received 401, attempting token refresh")
                val refreshResult = tokenManager.refreshTokens()

                if (refreshResult.isSuccess) {
                    val newAccessToken = tokenManager.getAccessToken()
                    if (newAccessToken != null) {
                        val newAuthHeader = "Bearer $newAccessToken"
                        val retryResponse = authApi.changePassword(newAuthHeader, request)

                        if (retryResponse.isSuccessful) {
                            Log.d(TAG, "Password changed successfully after token refresh")
                            Result.success(Unit)
                        } else {
                            val errorMsg = parseErrorMessage(retryResponse)
                            Log.e(TAG, "Error changing password after refresh: $errorMsg")
                            Result.failure(Exception(errorMsg))
                        }
                    } else {
                        Result.failure(Exception("Failed to get new access token"))
                    }
                } else {
                    Log.e(TAG, "Token refresh failed")
                    Result.failure(Exception("Session expired. Please log in again."))
                }
            } else {
                val errorMsg = parseErrorMessage(response)
                Log.e(TAG, "Error changing password: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during password change", e)
            val errorMessage = handleNetworkError(e)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun deleteTokens() {
        withContext(Dispatchers.IO) {
            tokenManager.clearTokens()
        }
    }
}