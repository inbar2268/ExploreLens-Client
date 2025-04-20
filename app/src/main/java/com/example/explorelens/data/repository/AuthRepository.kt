package com.example.explorelens.data.repository

import android.content.Context
import com.example.explorelens.data.model.GoogleSignInRequest
import com.example.explorelens.data.model.LoginRequest
import com.example.explorelens.data.model.LoginResponse
import com.example.explorelens.data.model.RegisterRequest
import com.example.explorelens.data.network.auth.AuthApi
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.network.auth.AuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthRepository(private val context: Context) {

    private val authApi: AuthApi = AuthClient.authApi
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

    // Check if the user is logged in
    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    // Get the stored access token
    fun getAccessToken(): String? {
        return tokenManager.getAccessToken()
    }

    // Get the stored refresh token
    fun getRefreshToken(): String? {
        return tokenManager.getRefreshToken()
    }

    // Clear user data and tokens (log out)
    suspend fun deleteTokens() {
        withContext(Dispatchers.IO) {
            tokenManager.clearTokens()
        }
    }
}
