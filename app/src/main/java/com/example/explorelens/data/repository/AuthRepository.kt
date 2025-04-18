package com.example.explorelens.data.repository

import android.content.Context
import com.example.explorelens.data.network.auth.AuthClient
import com.example.explorelens.data.network.auth.SecureTokenManager
import com.example.explorelens.data.db.AuthDatabase
import com.example.explorelens.data.db.User
import com.example.explorelens.data.model.*
import com.example.explorelens.utils.CredentialManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AuthRepository(private val context: Context) {

    private val userDao = AuthDatabase.getDatabase(context).userDao()
    private val authApi = AuthClient.authApi
    private val tokenManager = SecureTokenManager.getInstance(context)

    suspend fun saveUser(id: String, username: String, email: String) {
        val user = User(id, username, email)
        userDao.saveUser(user)
    }

    suspend fun getUser(): User? = userDao.getUser()

    suspend fun clearUser() = userDao.deleteUser()

    fun saveTokens(accessToken: String, refreshToken: String) {
        tokenManager.saveTokens(accessToken, refreshToken)
    }

    fun getAccessToken(): String? = tokenManager.getAccessToken()
    fun getRefreshToken(): String? = tokenManager.getRefreshToken()
    fun updateAccessToken(newAccessToken: String, newRefreshToken: String? = null) {
        tokenManager.updateAccessToken(newAccessToken, newRefreshToken)
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun setLoggedIn(loggedIn: Boolean) {
        tokenManager.setLoggedIn(loggedIn)
    }

    fun register(
        username: String,
        email: String,
        password: String,
        onSuccess: (RegisterResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = RegisterRequest(username, email, password)
        authApi.register(request).enqueue(object : Callback<RegisterResponse> {
            override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    GlobalScope.launch(Dispatchers.IO) {
                        saveUser(id = result._id, username = username, email = email)
                    }
                    onSuccess(result)
                } else {
                    onError(response.errorBody()?.string() ?: "Registration failed")
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                onError(t.message ?: "Network error")
            }
        })
    }

    fun login(
        email: String,
        password: String,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = LoginRequest(email, password)
        authApi.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!

                    GlobalScope.launch(Dispatchers.IO) {
                        saveUser(id = result._id, username = email.substringBefore('@'), email = email)
                        saveTokens(accessToken = result.accessToken, refreshToken = result.refreshToken)
                        setLoggedIn(true)
                    }

                    onSuccess(result)
                } else {
                    onError(response.errorBody()?.string() ?: "Login failed")
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                onError(t.message ?: "Network error")
            }
        })
    }

    fun googleSignIn(
        credentials: String,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = GoogleSignInRequest(credentials)
        authApi.googleSignIn(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!

                    GlobalScope.launch(Dispatchers.IO) {
                        // Note: User details will be set by the caller with the info from Google
                        saveTokens(accessToken = result.accessToken, refreshToken = result.refreshToken)
                        setLoggedIn(true)
                    }

                    onSuccess(result)
                } else {
                    onError(response.errorBody()?.string() ?: "Google sign-in failed")
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                onError(t.message ?: "Network error")
            }
        })
    }

    suspend fun logout(
        credentialManagerHelper: CredentialManagerHelper,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val refreshToken = getRefreshToken()

        // Clear credential manager state (Google Sign-In credentials)
        credentialManagerHelper.clearCredentials()

        if (refreshToken.isNullOrEmpty()) {
            clearUser()
            tokenManager.clearTokens()
            tokenManager.setLoggedIn(false)
            withContext(Dispatchers.Main) { onSuccess() }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            val request = LogoutRequest(refreshToken)
            authApi.logout(request).enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    GlobalScope.launch(Dispatchers.IO) {
                        clearUser()
                        tokenManager.clearTokens()
                        tokenManager.setLoggedIn(false)
                    }
                    onSuccess()
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    GlobalScope.launch(Dispatchers.IO) {
                        clearUser()
                        tokenManager.clearTokens()
                        tokenManager.setLoggedIn(false)
                    }
                    onSuccess()
                }
            })
        }
    }

    fun refreshToken(
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val refreshToken = getRefreshToken()

        if (refreshToken.isNullOrEmpty()) {
            onError("No refresh token available")
            return
        }

        val request = RefreshTokenRequest(refreshToken)
        authApi.refreshToken(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    tokenManager.updateAccessToken(result.accessToken, result.refreshToken)
                    onSuccess(result)
                } else {
                    tokenManager.clearTokens() // Clear tokens on refresh failure
                    tokenManager.setLoggedIn(false)
                    onError(response.errorBody()?.string() ?: "Token refresh failed")
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                tokenManager.clearTokens() // Clear tokens on network error
                tokenManager.setLoggedIn(false)
                onError(t.message ?: "Network error during token refresh")
            }
        })
    }

    fun observeAuthState(): Flow<Boolean> = flow {
        emit(isLoggedIn())
    }.flowOn(Dispatchers.IO)

    companion object {
        @Volatile private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}