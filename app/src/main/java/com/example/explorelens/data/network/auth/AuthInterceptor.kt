package com.example.explorelens.data.network.auth

import android.content.Context
import android.util.Log
import com.example.explorelens.data.model.RefreshTokenRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(private val context: Context) : Interceptor {
    private val TAG = "AuthInterceptor"
    private val tokenManager by lazy { AuthTokenManager.getInstance(context) }
    private val refreshMutex = Mutex()

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

            // Close the previous response body to free up resources
            response.close()

            // Use runBlocking for the interceptor, but use a Mutex to synchronize
            // token refresh operations across multiple interceptor calls.
            val newResponse = runBlocking {
                refreshMutex.withLock {
                    // Check if the token has already been refreshed by another concurrent request
                    // while this request was waiting for the mutex.
                    val currentAccessToken = tokenManager.getAccessToken()
                    if (currentAccessToken != null && currentAccessToken != accessToken) {
                        Log.d(TAG, "Token already refreshed by another interceptor. Retrying with new token.")
                        // If token was refreshed, retry the original request with the new token
                        val newRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $currentAccessToken")
                            .build()
                        return@withLock chain.proceed(newRequest)
                    }

                    // If we reach here, it means this is the first request to attempt refresh
                    // or the token hasn't been refreshed by another thread.
                    val newToken = refreshToken()

                    if (newToken != null) {
                        Log.d(TAG, "Token refreshed, retrying request")
                        val newRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                        return@withLock chain.proceed(newRequest)
                    } else {
                        Log.d(TAG, "Token refresh failed. Returning original 401.")
                        // If refresh failed, re-create the 401 response as the initial one was closed
                        // and the original request cannot be re-proceeded without a valid token.
                        // This allows the app to handle the logout flow.
                        return@withLock response // Return the original 401 response after closing it.
                    }
                }
            }
            return newResponse
        }

        return response
    }

    private suspend fun refreshToken(): String? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return try {
            val refreshTokenRequest = RefreshTokenRequest(refreshToken)
            val response = ExploreLensApiClient.authApi.refreshToken(refreshTokenRequest)

            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                tokenManager.saveAuthTokens(tokenResponse)
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