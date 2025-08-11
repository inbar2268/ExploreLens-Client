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
import com.example.explorelens.databinding.FragmentChangePasswordBinding
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
import kotlinx.coroutines.launch

class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var authRepository: AuthRepository
    private var isCurrentPasswordVisible = false
    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        authRepository = AuthRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Updated: Now using ImageButton instead of ImageView
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSubmitChange.setOnClickListener {
            attemptPasswordChange()
        }

        binding.tvShowCurrentPassword.setOnClickListener { toggleCurrentPasswordVisibility() }
        binding.tvShowNewPassword.setOnClickListener { toggleNewPasswordVisibility() }
        binding.tvShowConfirmPassword.setOnClickListener { toggleConfirmPasswordVisibility() }

        binding.etCurrentPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvShowCurrentPassword.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
    private fun toggleCurrentPasswordVisibility() {
        isCurrentPasswordVisible = !isCurrentPasswordVisible
        binding.etCurrentPassword.transformationMethod =
            if (isCurrentPasswordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()

        binding.tvShowCurrentPassword.text =
            if (isCurrentPasswordVisible) "Hide"
            else "Show"
        binding.etCurrentPassword.setSelection(binding.etCurrentPassword.text.length)
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

    private fun validateChangePasswordFields(): Boolean {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        var isValid = true

        if (currentPassword.isEmpty()) {
            binding.etCurrentPassword.error = "Current password is required"
            binding.tvShowCurrentPassword.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.etCurrentPassword.error = null
            binding.tvShowCurrentPassword.visibility = View.VISIBLE
        }

        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "New password is required"
            binding.tvShowNewPassword.visibility = View.INVISIBLE
            isValid = false
        } else if (newPassword.length < 6) {
            binding.etNewPassword.error = "Password must be at least 6 characters"
            binding.tvShowNewPassword.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.etNewPassword.error = null
            binding.tvShowNewPassword.visibility = View.VISIBLE
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Confirm password is required"
            binding.tvShowConfirmPassword.visibility = View.INVISIBLE
            isValid = false
        } else {
            binding.etConfirmPassword.error = null
            binding.tvShowConfirmPassword.visibility = View.VISIBLE
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.tvShowConfirmPassword.visibility = View.INVISIBLE
            isValid = false
        }

        if (currentPassword == newPassword && currentPassword.isNotEmpty()) {
            binding.etNewPassword.error = "New password must be different from current password"
            binding.tvShowNewPassword.visibility = View.INVISIBLE
            isValid = false
        }

        return isValid
    }

    private fun performPasswordChange() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()

        LoadingManager.showLoading(requireActivity())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.changePassword(currentPassword, newPassword)
            LoadingManager.hideLoading()

            result.fold(
                onSuccess = {
                    ToastHelper.showShortToast(context, "Password changed successfully!")
                    findNavController().navigateUp()
                },
                onFailure = { exception ->
                    val errorMessage = when {
                        exception.message?.contains("current password", ignoreCase = true) == true ->
                            "Current password is incorrect"
                        exception.message?.contains("network", ignoreCase = true) == true ->
                            "Changing password failed: Network error"
                        else -> exception.message ?: "Failed to change password"
                    }
                    ToastHelper.showShortToast(context, errorMessage)
                }
            )
        }
    }

    private fun attemptPasswordChange() {
        if (validateChangePasswordFields()) {
            performPasswordChange()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.etCurrentPassword.text?.clear()
        binding.etNewPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        binding.etCurrentPassword.error = null
        binding.etNewPassword.error = null
        binding.etConfirmPassword.error = null
        binding.tvShowCurrentPassword.visibility = View.INVISIBLE
        binding.tvShowNewPassword.visibility = View.INVISIBLE
        binding.tvShowConfirmPassword.visibility = View.INVISIBLE
        isCurrentPasswordVisible = false
        isNewPasswordVisible = false
        isConfirmPasswordVisible = false
        binding.etCurrentPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        binding.etNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        binding.etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }
}