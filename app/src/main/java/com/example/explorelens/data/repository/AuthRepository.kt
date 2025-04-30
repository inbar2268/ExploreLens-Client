package com.example.explorelens.data.repository

import android.content.Context
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

    // Login user
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

    suspend fun deleteTokens() {
        withContext(Dispatchers.IO) {
            tokenManager.clearTokens()
        }
    }
}
