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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.explorelens.MainActivity
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.databinding.FragmentRegisterBinding
import com.example.explorelens.data.network.auth.GoogleSignInHelper
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
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
            googleSignInHelper.processSignInResult(
                result.data,
                isRegistration = true,
                showLoading = { showLoading() },
                hideLoading = { hideLoading() },
                onSuccess = {
                    (requireActivity() as MainActivity).launchArActivity()
                }
            )
        } else {
            Log.w(TAG, "Google sign in failed or canceled, code: ${result.resultCode}")
            ToastHelper.showShortToast(context, "Google Sign-In canceled")
            hideLoading()
        }
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

        setupGoogleSignIn()
        setupListeners()
    }

    private fun setupGoogleSignIn() {
        googleSignInHelper = GoogleSignInHelper(this, authRepository)
        googleSignInHelper.configureGoogleSignIn()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnRegister.setOnClickListener { attemptRegistration() }
        binding.btnGoogleRegister.setOnClickListener { signUpWithGoogle() }
        binding.tvSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
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

    private fun signUpWithGoogle() {
        googleSignInHelper.performGoogleSignIn(
            googleSignInLauncher,
            isRegistration = true,
            showLoading = { showLoading() },
            hideLoading = { hideLoading() }
        )
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        binding.etPassword.transformationMethod = if (isPasswordVisible)
            HideReturnsTransformationMethod.getInstance()
        else
            PasswordTransformationMethod.getInstance()

        binding.ivPasswordToggle.setImageResource(
            if (isPasswordVisible)
                android.R.drawable.ic_menu_close_clear_cancel
            else
                android.R.drawable.ic_menu_view
        )

        binding.etPassword.setSelection(binding.etPassword.selectionEnd)
    }

    private fun attemptRegistration() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (!validateInputs(name, email, password)) return

        showLoading()

        CoroutineScope(Dispatchers.Main).launch {
            val result = authRepository.registerUser(name, email, password)
            hideLoading()

            if (result.isSuccess) {
                ToastHelper.showShortToast(context, "Registration successful")
                (requireActivity() as MainActivity).launchArActivity()
            } else {
                Log.d(TAG, "Registration failed: ${result.exceptionOrNull()?.message}")
                ToastHelper.showShortToast(
                    context, "Registration failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
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
            binding.ivPasswordToggle.visibility = View.INVISIBLE
            binding.etPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.ivPasswordToggle.visibility = View.VISIBLE
        }

        return isValid
    }

    private fun showLoading() {
        LoadingManager.showLoading(requireActivity())
        binding.btnRegister.isEnabled = false
        binding.btnGoogleRegister.isEnabled = false
    }

    private fun hideLoading() {
        LoadingManager.hideLoading()
        binding.btnRegister.isEnabled = true
        binding.btnGoogleRegister.isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        binding.etName.text?.clear()
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.etName.error = null
        binding.etEmail.error = null
        binding.etPassword.error = null
        binding.ivPasswordToggle.visibility = View.INVISIBLE
        isPasswordVisible = false
        binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_view)
        hideLoading()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }

}