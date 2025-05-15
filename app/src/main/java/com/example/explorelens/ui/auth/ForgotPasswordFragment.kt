package com.example.explorelens.ui.auth

import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.databinding.FragmentForgotPasswordBinding
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        authRepository = AuthRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnResetPassword.setOnClickListener {
            attemptPasswordReset()
        }
    }

    private fun attemptPasswordReset() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            return
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            return
        }

        LoadingManager.showLoading(requireActivity())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.forgotPassword(email)
            LoadingManager.hideLoading()

            result.fold(
                onSuccess = {
                    ToastHelper.showShortToast(context, "Password reset email sent.")
                    findNavController().navigate(R.id.action_forgotPasswordFragment_to_resetPasswordFragment)
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Failed to send password reset email"
                    ToastHelper.showShortToast(context, errorMessage)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        binding.etEmail.text?.clear()
        binding.etEmail.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }
}