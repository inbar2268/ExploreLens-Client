package com.example.explorelens.ui.auth

import android.app.Activity
import android.content.Intent
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
import com.example.explorelens.data.model.RegisterRequest
import com.example.explorelens.databinding.FragmentRegisterBinding
import com.example.explorelens.data.network.auth.GoogleSignInHelper
import com.example.explorelens.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var isPasswordVisible = false
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var authRepository: AuthRepository
    private val TAG = "RegisterFragment"

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
                        binding.btnRegister.isEnabled = true
                        binding.btnGoogleRegister.isEnabled = true

                        if (success && authResponse != null) {
                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                            // Only navigate AFTER server confirms authentication
                            findNavController().navigate(R.id.action_registerFragment_to_profileFragment)
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
                    binding.btnRegister.isEnabled = true
                    binding.btnGoogleRegister.isEnabled = true

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
            binding.progressBar.visibility = View.GONE        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        authRepository = AuthRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Google Sign-In Helper
        googleSignInHelper = GoogleSignInHelper(this)
        googleSignInHelper.configureGoogleSignIn()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnRegister.setOnClickListener {
            attemptRegistration()
        }

        binding.btnGoogleRegister.setOnClickListener {
            signUpWithGoogle()
        }

        binding.tvSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
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

    private fun signUpWithGoogle() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false
        binding.btnGoogleRegister.isEnabled = false

        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
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

        val selection = binding.etPassword.selectionEnd
        binding.etPassword.setSelection(selection)
    }

    private fun attemptRegistration() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        var isValid = true

        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            isValid = false
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            isValid = false
        } else{
            binding.ivPasswordToggle.visibility = View.VISIBLE
        }

        if (isValid) {
            CoroutineScope(Dispatchers.Main).launch {
                val result = authRepository.registerUser(name, email, password)
                if (result.isSuccess) {
                    Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_registerFragment_to_profileFragment)
                } else {
                    Toast.makeText(
                        context,
                        "Registration failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}