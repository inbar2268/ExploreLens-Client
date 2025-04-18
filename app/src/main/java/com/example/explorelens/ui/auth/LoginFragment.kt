package com.example.explorelens.ui.auth

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.data.model.RegisterRequest
import com.example.explorelens.databinding.FragmentLoginBinding
import com.example.explorelens.data.network.auth.GoogleSignInHelper
import com.example.explorelens.data.network.auth.GoogleSignInHelper.Companion.isUserAuthenticatedWithGoogle
import com.example.explorelens.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var isPasswordVisible = false
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var authRepository: AuthRepository
    private val TAG = "LoginFragment"

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            googleSignInHelper.handleSignInResult(task) { account ->
                val idToken = account.idToken
                if (idToken != null) {

                    googleSignInHelper.sendCredentialsToServer(idToken) { success, authResponse ->
                        // Hide loading indicator
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        binding.btnGoogleLogin.isEnabled = true

                        if (success && authResponse != null) {
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                            // Only navigate AFTER server confirms authentication
                            findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
                        } else {
                            Toast.makeText(
                                context,
                                "Server authentication failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // Hide loading indicator also on failure
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.btnGoogleLogin.isEnabled = true

                    Log.e(TAG, "ID token is null")
                    Toast.makeText(
                        context,
                        "Google authentication failed: No credentials",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Log.w(TAG, "Google sign in failed or canceled, code: ${result.resultCode}")
            Toast.makeText(context, "Google Sign-In canceled", Toast.LENGTH_SHORT).show()
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        authRepository = AuthRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authRepository = AuthRepository(requireContext())

        googleSignInHelper = GoogleSignInHelper(this)
        googleSignInHelper.configureGoogleSignIn()

        if (isUserAuthenticatedWithGoogle(requireContext()) || authRepository.isLoggedIn()) {
            Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
            return
        }

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.btnGoogleLogin.setOnClickListener {
            signInWithGoogle()
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
                binding.ivPasswordToggle.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun signInWithGoogle() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnGoogleLogin.isEnabled = false

        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        binding.etPassword.transformationMethod =
            if (isPasswordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()

        binding.ivPasswordToggle.setImageResource(
            if (isPasswordVisible)
                android.R.drawable.ic_menu_close_clear_cancel
            else
                android.R.drawable.ic_menu_view
        )
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            return
        } else {
            binding.ivPasswordToggle.visibility = View.VISIBLE
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnGoogleLogin.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val result = authRepository.loginUser(email, password)
            if (result.isSuccess) {
                Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
            } else {
                Toast.makeText(context, "Login failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }

            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            binding.btnGoogleLogin.isEnabled = true
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
