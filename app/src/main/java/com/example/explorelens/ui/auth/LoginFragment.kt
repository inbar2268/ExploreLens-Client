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
import com.example.explorelens.MainActivity
import com.example.explorelens.R
import com.example.explorelens.databinding.FragmentLoginBinding
import com.example.explorelens.data.network.auth.GoogleSignInHelper
import com.example.explorelens.data.network.auth.GoogleSignInHelper.Companion.isUserAuthenticatedWithGoogle
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        Log.d(TAG, "Google Sign-In result received. Code: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Result OK, handling sign-in result...")
            googleSignInHelper.processSignInResult(
                result.data,
                isRegistration = false,
                showLoading = { showLoading() },
                hideLoading = { hideLoading() },
                onSuccess = {
                    (requireActivity() as MainActivity).launchArActivity()
                }
            )
        } else {
            Log.w(TAG, "Google sign in failed or canceled, code: ${result.resultCode}")
            Toast.makeText(context, "Google Sign-In canceled", Toast.LENGTH_SHORT).show()
            hideLoading()
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

        setupGoogleSignIn()
        checkExistingLogin()
        setupListeners()
    }

    private fun setupGoogleSignIn() {
        googleSignInHelper = GoogleSignInHelper(this, authRepository)
        googleSignInHelper.configureGoogleSignIn()
    }

    private fun checkExistingLogin() {
        if (isUserAuthenticatedWithGoogle(requireContext()) || authRepository.isLoggedIn()) {
            Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_loginFragment_to_profileFragment)
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnGoogleLogin.setOnClickListener { signInWithGoogle() }
        binding.tvSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        }
        binding.ivPasswordToggle.setOnClickListener { togglePasswordVisibility() }

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.ivPasswordToggle.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun signInWithGoogle() {
        googleSignInHelper.performGoogleSignIn(
            googleSignInLauncher,
            isRegistration = false,
            showLoading = { showLoading() },
            hideLoading = { hideLoading() }
        )
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

        var isValid = true

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            isValid = false
        } else{
            binding.ivPasswordToggle.visibility = View.VISIBLE

        }

        if(isValid){
            showLoading()
            CoroutineScope(Dispatchers.Main).launch {
                val result = authRepository.loginUser(email, password)
                hideLoading()

                if (result.isSuccess) {
                    Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                    (requireActivity() as MainActivity).launchArActivity()
                } else {
                    Log.d(TAG, "Login failed: ${result.exceptionOrNull()?.message}")
                    Toast.makeText(context, "Login failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else{
            return
        }

    }

    private fun showLoading() {
        LoadingManager.showLoading(requireActivity())
        binding.btnLogin.isEnabled = false
        binding.btnGoogleLogin.isEnabled = false
    }

    private fun hideLoading() {
        LoadingManager.hideLoading()
        binding.btnLogin.isEnabled = true
        binding.btnGoogleLogin.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.etEmail.error = null
        binding.etPassword.error = null
        binding.ivPasswordToggle.visibility = View.INVISIBLE
        isPasswordVisible = false
        binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_view)
        hideLoading()
    }
}