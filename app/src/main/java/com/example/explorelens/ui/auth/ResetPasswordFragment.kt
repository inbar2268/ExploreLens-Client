package com.example.explorelens.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.databinding.FragmentResetPasswordBinding
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var authRepository: AuthRepository
    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        authRepository = AuthRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSubmitReset.setOnClickListener {
            attemptPasswordReset()
        }

        binding.tvShowNewPassword.setOnClickListener { toggleNewPasswordVisibility() }
        binding.tvShowConfirmPassword.setOnClickListener { toggleConfirmPasswordVisibility() }

        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvShowNewPassword.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvShowConfirmPassword.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun toggleNewPasswordVisibility() {
        isNewPasswordVisible = !isNewPasswordVisible
        binding.etNewPassword.transformationMethod =
            if (isNewPasswordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()

        binding.tvShowNewPassword.text =
            if (isNewPasswordVisible) "Hide"
            else "Show"
        binding.etNewPassword.setSelection(binding.etNewPassword.text.length)
    }

    private fun toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible
        binding.etConfirmPassword.transformationMethod =
            if (isConfirmPasswordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()

        binding.tvShowConfirmPassword.text =
            if (isConfirmPasswordVisible) "Hide"
            else "Show"
        binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)
    }

    private fun validateResetPasswordFields(): Boolean {
        val token = binding.etToken.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        var isValid = true

        if (token.isEmpty()) {
            binding.etToken.error = "Token is required"
            isValid = false
        } else {
            binding.etToken.error = null // Clear error if not empty
        }

        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "Password is required"
            binding.tvShowNewPassword.visibility = View.INVISIBLE
            isValid = false
        } else if (newPassword.length < 6) {
            binding.etNewPassword.error = "Password is required"
            binding.tvShowNewPassword.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.etNewPassword.error = null // Clear error if not empty
            binding.tvShowNewPassword.visibility = View.VISIBLE
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Confirm password is required"
            binding.tvShowConfirmPassword.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.etConfirmPassword.error = null // Clear error if not empty
            binding.tvShowConfirmPassword.visibility = View.VISIBLE
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.tvShowConfirmPassword.visibility = View.INVISIBLE
            isValid = false
        }

        return isValid
    }

    private fun performPasswordReset() {
        val token = binding.etToken.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString()

        LoadingManager.showLoading(requireActivity())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.resetPassword(token, newPassword)
            LoadingManager.hideLoading()

            result.fold(
                onSuccess = {
                    ToastHelper.showShortToast(context, "Password has been reset successfully!")
                    findNavController().navigate(R.id.action_resetPasswordFragment_to_loginFragment)
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Failed to reset password"
                    ToastHelper.showShortToast(context, errorMessage)
                    // Visibility of show/hide is handled in validation
                }
            )
        }
    }

    private fun attemptPasswordReset() {
        if (validateResetPasswordFields()) {
            performPasswordReset()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.etToken.text?.clear()
        binding.etNewPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        binding.etToken.error = null
        binding.etNewPassword.error = null
        binding.etConfirmPassword.error = null
        binding.tvShowNewPassword.visibility = View.INVISIBLE // Hide on resume
        binding.tvShowConfirmPassword.visibility = View.INVISIBLE // Hide on resume
        isNewPasswordVisible = false
        isConfirmPasswordVisible = false
        binding.etNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        binding.etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }
}