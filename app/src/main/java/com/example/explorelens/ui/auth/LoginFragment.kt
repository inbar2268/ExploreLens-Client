package com.example.explorelens.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.databinding.FragmentLoginBinding
import com.example.explorelens.utils.CredentialManagerHelper
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var isPasswordVisible = false

    private lateinit var authRepository: AuthRepository
    private lateinit var credentialManagerHelper: CredentialManagerHelper

    private companion object {
        private const val TAG = "GOOGLE_SIGN_IN"
    }

    // Register activity result launcher for Credential Manager
    private val getCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // When the result is OK, we need to retry getting credentials
            // This is the standard pattern for Credential Manager
            signInWithGoogle(isRetry = true)
        } else {
            Log.d(TAG, "Credential selection was canceled")
            Toast.makeText(context, "Sign-in canceled", Toast.LENGTH_SHORT).show()
            resetUiState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        authRepository = AuthRepository.getInstance(requireContext())
        credentialManagerHelper = CredentialManagerHelper.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.btnGoogleLogin.setOnClickListener {
            signInWithGoogle(isRetry = false)
        }

        binding.tvSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        }

        binding.ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.ivPasswordToggle.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            if (authRepository.isLoggedIn()) {
                findNavController().navigate(R.id.action_loginFragment_to_arActivity)
                Toast.makeText(context, "Already logged in", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithGoogle(isRetry: Boolean = false) {
        lifecycleScope.launch {
            try {
                setLoadingState(true)

                // Get Google Sign-In request using the helper
                val serverClientId = getString(R.string.google_web_client_id)

                // First, try with authorized accounts filter
                val useAuthorizedAccountsOnly = !isRetry
                val request = credentialManagerHelper.buildGoogleSignInRequest(
                    serverClientId = serverClientId,
                    filterByAuthorizedAccounts = useAuthorizedAccountsOnly,
                    autoSelectEnabled = true
                )

                val credentialManager = credentialManagerHelper.getCredentialManager()

                try {
                    // Try to get credential
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext()
                    )

                    // Process the credential response
                    val googleTokenInfo = credentialManagerHelper.processGoogleCredential(result)

                    if (googleTokenInfo != null) {
                        // Handle successful sign-in
                        handleGoogleSignIn(googleTokenInfo)
                    } else {
                        showSignInError("Failed to process Google Sign-In")
                    }

                } catch (e: GetCredentialException) {
                    // Handle the exception - may require user interaction
//                    if (e.intentSender != null && !isRetry) {
//                        // Launch the credential picker
//                        val intentSenderRequest = IntentSenderRequest.Builder(e.intentSender).build()
//                        getCredentialLauncher.launch(intentSenderRequest)
//                    } else {
//                        // If this is already a retry or there's no intent sender,
//                        // try sign-up flow (showing all accounts) if we were filtering
//                        if (useAuthorizedAccountsOnly) {
//                            // If we were filtering by authorized accounts, now try with all accounts
//                            signInWithGoogle(true)
//                        } else {
//                            Log.e(TAG, "Error during Google sign-in with no resolution", e)
//                            showSignInError("Google Sign-In failed")
//                        }
//                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Google sign-in", e)
                showSignInError("Google Sign-In failed: ${e.message}")
            }
        }
    }

    private fun handleGoogleSignIn(googleTokenInfo: CredentialManagerHelper.GoogleIdTokenInfo) {
        Log.d(TAG, "Successfully obtained Google ID token for: ${googleTokenInfo.displayName} (${googleTokenInfo.email})")

        // Authenticate with your backend
        authRepository.googleSignIn(
            credentials = googleTokenInfo.idToken,
            onSuccess = { response ->
                resetUiState()
                // Save the email obtained from Google
                lifecycleScope.launch {
                    authRepository.saveUser(
                        id = response._id,
                        username = googleTokenInfo.displayName ?: googleTokenInfo.email.substringBefore('@'),
                        email = googleTokenInfo.email
                    )
                }

                Toast.makeText(context, "Google Sign-In successful", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_arActivity)
            },
            onError = { errorMessage ->
                resetUiState()
                Log.e(TAG, "Backend authentication failed: $errorMessage")
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showSignInError(message: String) {
        resetUiState()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnGoogleLogin.isEnabled = !isLoading
        binding.btnLogin.isEnabled = !isLoading
    }

    private fun resetUiState() {
        setLoadingState(false)
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        if (isPasswordVisible) {
            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_view)
        }
        binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
    }

    private fun attemptLogin() {
        var isValid = true
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.ivPasswordToggle.visibility = View.VISIBLE
        }

        if (!isValid) {
            return
        }

        setLoadingState(true)

        authRepository.login(
            email = email,
            password = password,
            onSuccess = { response ->
                resetUiState()
                Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_arActivity)
            },
            onError = { errorMessage ->
                Log.e("LOGIN", "Login failed: $errorMessage")
                resetUiState()
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}