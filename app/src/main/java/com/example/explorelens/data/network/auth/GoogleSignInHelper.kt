package com.example.explorelens.data.network.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.explorelens.BuildConfig
import com.example.explorelens.R
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
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

class GoogleSignInHelper(private val fragment: Fragment, private val authRepository: AuthRepository) {
    private val TAG = "GoogleSignInHelper"
    private lateinit var googleSignInClient: GoogleSignInClient

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
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent.apply {
            // Force account picker to appear every time
            putExtra("com.google.android.gms.auth.ACCOUNT_SELECTION_OPTIONS", true)
        }
    }

    // Handle Google Sign-In result
    fun handleSignInResult(
        completedTask: Task<GoogleSignInAccount>,
        onSuccessListener: (String) -> Unit
    ) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                onSuccessListener(idToken)
            } else {
                Toast.makeText(fragment.context, "Google Sign-In failed: No ID token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
            Toast.makeText(
                fragment.context,
                "Google Sign-In failed: ${e.statusCode}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }

    // New unified method for handling the complete sign-in process
    fun performGoogleSignIn(
        launcher: ActivityResultLauncher<Intent>,
        isRegistration: Boolean,
        showLoading: () -> Unit,
        hideLoading: () -> Unit
    ) {
        showLoading()

        try {
            // Sign out first to force account selection
            signOut {
                Log.d(TAG, "Signed out before sign-in to force account selection.")
                val signInIntent = getSignInIntent()
                hideLoading() // Temporarily hide loading during account selection
                launcher.launch(signInIntent)
            }
        } catch (e: Exception) {
            hideLoading()
            Log.e(TAG, "Error launching Google Sign-In: ${e.message}")
            Toast.makeText(fragment.context, "Failed to start Google Sign-In", Toast.LENGTH_SHORT).show()
        }
    }

    // Process the result from Google Sign-In
    fun processSignInResult(
        intent: Intent?,
        isRegistration: Boolean,
        showLoading: () -> Unit,
        hideLoading: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(intent)

        handleSignInResult(task) { idToken ->
            showLoading()

            CoroutineScope(Dispatchers.Main).launch {
                val result = authRepository.googleSignIn(idToken)
                hideLoading()

                if (result.isSuccess) {
                    val successMessage = if (isRegistration) "Registration successful!" else "Login successful!"
                    Toast.makeText(fragment.context, successMessage, Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    val errorPrefix = if (isRegistration) "Google registration" else "Google login"
                    Log.e(TAG, "$errorPrefix failed: ${result.exceptionOrNull()?.message}")
                    Toast.makeText(
                        fragment.context,
                        "$errorPrefix failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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