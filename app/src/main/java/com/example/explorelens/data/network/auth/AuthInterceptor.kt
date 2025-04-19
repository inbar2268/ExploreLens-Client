package com.example.explorelens.data.network.auth

import android.content.Context
import android.util.Log
import com.example.explorelens.data.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(private val context: Context) : Interceptor {
    private val TAG = "AuthInterceptor"
    private val tokenManager by lazy { AuthTokenManager.getInstance(context) }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip authentication for auth-related endpoints
        if (shouldSkipAuth(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Add access token to request if available
        val accessToken = tokenManager.getAccessToken()
        val initialRequest = if (accessToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        // Execute the request
        var response = chain.proceed(initialRequest)

        // If we get a 401 Unauthorized, try refreshing the token
        if (response.code == 401) {
            Log.d(TAG, "Received 401 response, attempting token refresh")

            // Close the previous response
            response.close()

            // Try to refresh the token
            val newToken = runBlocking {
                refreshToken()
            }

            // If refresh was successful, retry with the new token
            if (newToken != null) {
                Log.d(TAG, "Token refreshed, retrying request")
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()

                return chain.proceed(newRequest)
            } else {
                Log.d(TAG, "Token refresh failed, proceeding with original request")
                // If refresh failed, proceed with the original request without auth header
                // This will likely result in another 401, but the app can handle that case
                return chain.proceed(originalRequest)
            }
        }

        return response
    }

    private suspend fun refreshToken(): String? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return try {
            val refreshTokenRequest = RefreshTokenRequest(refreshToken)
            val response = AuthClient.authApi.refreshToken(refreshTokenRequest)

            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                tokenManager.saveAuthTokensLogin(tokenResponse)
                tokenResponse.accessToken
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code()} ${response.message()}")
                // Token refresh failed, need to clear tokens and log out
                tokenManager.clearTokens()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
            tokenManager.clearTokens()
            null
        }
    }

    private fun shouldSkipAuth(request: Request): Boolean {
        val url = request.url.toString()
        return url.contains("/auth/login") ||
                url.contains("/auth/register") ||
                url.contains("/auth/google") ||
                url.contains("/auth/refresh")
    }
}