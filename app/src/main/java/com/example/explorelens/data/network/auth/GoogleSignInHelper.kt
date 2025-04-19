package com.example.explorelens.data.network.auth

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.explorelens.BuildConfig
import com.example.explorelens.data.model.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class GoogleSignInHelper(private val fragment: Fragment) {
    private val TAG = "GoogleSignInHelper"
    private lateinit var googleSignInClient: GoogleSignInClient
    private val authApi: AuthApi = AuthClient.authApi
    private val tokenManager: AuthTokenManager by lazy {
        AuthTokenManager.getInstance(fragment.requireContext())
    }

    val WEB_CLIENT_ID = BuildConfig.WEB_CLIENT_ID

    // Initialize Google Sign-In client
    fun configureGoogleSignIn() {
        // Configure sign-in to request the user's ID, email address, and basic profile
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(WEB_CLIENT_ID)
            .build()

        // Build a GoogleSignInClient with the options specified by gso
        googleSignInClient = GoogleSignIn.getClient(fragment.requireActivity(), gso)
    }

    // Get sign-in intent to launch Google Sign-In flow
    fun getSignInIntent() = googleSignInClient.signInIntent

    // Handle Google Sign-In result
    fun handleSignInResult(completedTask: Task<GoogleSignInAccount>, onSuccessListener: (GoogleSignInAccount) -> Unit) {
        try {
            // Get Google Sign-In account
            val account = completedTask.getResult(ApiException::class.java)

            // Call the success listener with the account
            onSuccessListener(account)

        } catch (e: ApiException) {
            // Sign in failed
            Log.w(TAG, "Google sign in failed", e)
            Toast.makeText(
                fragment.context,
                "Google Sign-In failed: ${e.statusCode}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Send Google credentials to your server
    fun sendCredentialsToServer(idToken: String, onComplete: (Boolean, LoginResponse?) -> Unit) {
        // Create request body with the Google ID token
        val googleAuthRequest = GoogleSignInRequest(idToken)

        // Use Coroutines for network call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send the token to your server
                val response: Response<LoginResponse> = authApi.googleSignIn(googleAuthRequest)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val authResponse = response.body()
                        if (authResponse != null) {
                            // Save tokens to encrypted shared preferences
                            tokenManager.saveAuthTokensLogin(authResponse)

                            // Call completion handler with success
                            onComplete(true, authResponse)
                        } else {
                            // Empty response body
                            onComplete(false, null)
                        }
                    } else {
                        // Error response from server
                        val errorMsg = response.errorBody()?.string() ?: "Authentication failed"
                        Log.e(TAG, "Server error: $errorMsg")
                        Toast.makeText(
                            fragment.context,
                            "Server authentication failed: $errorMsg",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete(false, null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Network error", e)
                    Toast.makeText(
                        fragment.context,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete(false, null)
                }
            }
        }
    }

    companion object {
        fun isUserAuthenticatedWithGoogle(context: Context): Boolean {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val tokenManager = AuthTokenManager.getInstance(context)
            return account != null && tokenManager.isLoggedIn()
        }
    }
}