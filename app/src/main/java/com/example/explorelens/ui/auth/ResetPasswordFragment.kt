package com.example.explorelens.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.databinding.FragmentResetPasswordBinding
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.utils.LoadingManager
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var authRepository: AuthRepository

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
    }

    private fun attemptPasswordReset() {
        val token = binding.etToken.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        // Validate inputs
        if (token.isEmpty()) {
            binding.etToken.error = "Token is required"
            return
        }

        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "Password is required"
            return
        }

        if (newPassword.length < 6) {
            binding.etNewPassword.error = "Password must be at least 6 characters"
            return
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return
        }

        LoadingManager.showLoading(requireActivity())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.resetPassword(token, newPassword)
            LoadingManager.hideLoading()

            result.fold(
                onSuccess = {
                    Toast.makeText(context, "Password has been reset successfully!", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_resetPasswordFragment_to_loginFragment)
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Failed to reset password"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LoadingManager.hideLoading()
        _binding = null
    }
}
