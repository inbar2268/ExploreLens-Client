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
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.databinding.FragmentLoginBinding
import com.example.explorelens.data.network.auth.GoogleSignInHelper
import com.example.explorelens.data.network.auth.GoogleSignInHelper.Companion.isUserAuthenticatedWithGoogle
import com.google.android.gms.auth.api.signin.GoogleSignIn

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var isPasswordVisible = false
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private val TAG = "LoginFragment"

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            googleSignInHelper.handleSignInResult(task) { account ->
                val idToken = account.idToken
                if (idToken != null) {
                    Toast.makeText(
                        context,
                        "Google Sign-In successful, connecting to server...",
                        Toast.LENGTH_SHORT
                    ).show()

                    googleSignInHelper.sendCredentialsToServer(idToken) { success, authResponse ->
                        if (success && authResponse != null) {
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_loginFragment_to_arActivity)
                        } else {
                            Toast.makeText(
                                context,
                                "Server authentication failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
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
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        googleSignInHelper = GoogleSignInHelper(this)
        googleSignInHelper.configureGoogleSignIn()

        if (isUserAuthenticatedWithGoogle(requireContext())) {
            Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_loginFragment_to_arActivity)
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
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
        } else {
            binding.ivPasswordToggle.visibility = View.VISIBLE
        }

        if (email.isNotEmpty() && password.isNotEmpty()) {
            // Simulate successful login
            Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_loginFragment_to_arActivity)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
