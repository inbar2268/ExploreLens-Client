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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var isPasswordVisible = false

    private lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        authRepository = AuthRepository.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnRegister.setOnClickListener {
            attemptRegistration()
        }

        binding.btnGoogleRegister.setOnClickListener {
            Toast.makeText(context, "Google Sign-Up clicked", Toast.LENGTH_SHORT).show()
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

        lifecycleScope.launch {
            if (authRepository.isLoggedIn()) {
                findNavController().navigate(R.id.action_registerFragment_to_arActivity)
                Toast.makeText(context, "Already logged in", Toast.LENGTH_SHORT).show()
            }
        }
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
            binding.progressBar.visibility = View.VISIBLE
            binding.btnRegister.isEnabled = false

            authRepository.register(
                username = name,
                email = email,
                password = password,
                onSuccess = { response ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true

                    Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_registerFragment_to_arActivity)
                },
                onError = { errorMessage ->
                    Log.e("REGISTER", "Registration failed: $errorMessage")
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}