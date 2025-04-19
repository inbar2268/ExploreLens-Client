package com.example.explorelens.data.repository

import android.content.Context
import com.example.explorelens.data.model.LoginRequest
import com.example.explorelens.data.model.LoginResponse
import com.example.explorelens.data.model.RegisterRequest
import com.example.explorelens.data.model.RegisterResponse
import com.example.explorelens.data.network.auth.AuthApi
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.network.auth.AuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthRepository(private val context: Context) {

    private val authApi: AuthApi = AuthClient.authApi
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    // Register user
//    suspend fun registerUser(username: String, email: String, password: String): Result<RegisterResponse> {
//        val registerRequest = RegisterRequest(username, email, password)
//        return try {
//            val response: Response<RegisterResponse> = authApi.register(registerRequest)
//            if (response.isSuccessful) {
//                response.body()?.let { authResponse ->
//                    // Save tokens securely
//                    tokenManager.saveAuthTokensRegister(authResponse)
//                    Result.success(authResponse)
//                } ?: Result.failure(Exception("Registration failed: No response from server"))
//            } else {
//                Result.failure(Exception("Registration failed: ${response.errorBody()?.string()}"))
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }


    suspend fun registerUser(username: String, email: String, password: String): Result<RegisterResponse> {
        return try {
            val registerRequest = RegisterRequest(username, email, password)

            val mockResponse = RegisterResponse(
                _id = "6800b714eb49a86f1666bb60",
                username = username,
                email = email,
                accessToken = "mockAccessToken",
                refreshToken = "mockRefreshToken"
            )

            // Save tokens securely
            tokenManager.saveAuthTokensRegister(mockResponse)
            Result.success(mockResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // Login user
//    suspend fun loginUser(email: String, password: String): Result<LoginResponse> {
//        return try {
//            val loginRequest = LoginRequest(email, password)
//            val response: Response<LoginResponse> = authApi.login(loginRequest)
//            if (response.isSuccessful) {
//                response.body()?.let { authResponse ->
//                    // Save tokens securely
//                    tokenManager.saveAuthTokensLogin(authResponse)
//                    Result.success(authResponse)
//                } ?: Result.failure(Exception("Login failed: No response from server"))
//            } else {
//                Result.failure(Exception("Login failed: ${response.errorBody()?.string()}"))
//            }
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }


    suspend fun loginUser(email: String, password: String): Result<LoginResponse> {
        return try {
            // Mocked successful response
            val mockResponse = LoginResponse(
                _id = "6800b714eb49a86f1666bb60",
                accessToken = "mockAccessToken",
                refreshToken = "mockRefreshToken"
            )

            // Save tokens securely
            tokenManager.saveAuthTokensLogin(mockResponse)
            Result.success(mockResponse)

        } catch (e: Exception) {
            Result.failure(e)
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
