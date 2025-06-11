package com.example.explorelens.ui.user

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.databinding.FragmentSettingsBinding
import com.example.explorelens.databinding.DialogLogoutBinding
import com.example.explorelens.databinding.DialogDeleteUserBinding
import com.example.explorelens.databinding.DialogResetHistoryBinding
import com.example.explorelens.databinding.DialogMapTypeBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var siteHistoryRepository: SiteHistoryRepository
    private lateinit var tokenManager: AuthTokenManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root

        // Initialize repositories
        userRepository = UserRepository(requireContext())
        authRepository = AuthRepository(requireContext())
        siteHistoryRepository = SiteHistoryRepository(requireContext())
        tokenManager = AuthTokenManager.getInstance(requireContext())

        binding.backButton.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileFragment)
        }

        binding.editProfileRow.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_editProfileFragment)
        }

        binding.changePasswordRow.setOnClickListener {
            // Navigate to change password screen
            // Example: startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        binding.deleteAccountRow.setOnClickListener {
            showDeleteUserDialog()
        }

        binding.resetHistoryRow.setOnClickListener {
            showResetHistoryDialog()
        }

        binding.changeMapTypeRow.setOnClickListener {
            showMapTypeDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutDialog()
        }

        loadCurrentMapType()

        return view
    }

    private fun showDeleteUserDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogBinding = DialogDeleteUserBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.deleteButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = userRepository.deleteUser()
                    result?.onSuccess {
                        authRepository.logout()
                        dialog.dismiss()
                        findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
                        ToastHelper.showShortToast(context, "Account deleted")
                    }
                    result?.onFailure { exception ->
                        ToastHelper.showShortToast(context, "Failed to delete account")
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    ToastHelper.showShortToast(context, "Failed to delete account")
                    dialog.dismiss()
                }
            }
        }
        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.show()
    }

    private fun showResetHistoryDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogBinding = DialogResetHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.resetButton.setOnClickListener {
            // Disable button to prevent multiple clicks
            dialogBinding.resetButton.isEnabled = false
            dialogBinding.resetButton.text = "Resetting..."

            lifecycleScope.launch {
                try {
                    val userId = tokenManager.getUserId()
                    if (userId != null) {
                        // Get count before reset
                        val historyCount = siteHistoryRepository.getSiteHistoryCount(userId)

                        if (historyCount == 0) {
                            ToastHelper.showShortToast(context, "No site history to reset")
                            return@launch
                        }

                        // Call the reset function and handle the Result
                        siteHistoryRepository.resetSiteHistoryForUser(userId).fold(
                            onSuccess = {
                                ToastHelper.showShortToast(context, "Site history reset successfully ($historyCount items removed)")
                            },
                            onFailure = { exception ->
                                val errorMessage = when {
                                    exception.message?.contains("network", ignoreCase = true) == true ->
                                        "Network error. Please check your connection."
                                    exception.message?.contains("server", ignoreCase = true) == true ->
                                        "Server error. Please try again later."
                                    else -> "Failed to reset site history: ${exception.message}"
                                }
                                ToastHelper.showShortToast(context, errorMessage)
                            }
                        )
                    } else {
                        ToastHelper.showShortToast(context, "Unable to reset history - user not found")
                    }
                } finally {
                    // Re-enable button and restore text
                    dialogBinding.resetButton.isEnabled = true
                    dialogBinding.resetButton.text = "Reset"
                    dialog.dismiss()
                }
            }
        }

        // Set dialog width to match parent
        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.show()
    }

    private fun showMapTypeDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogBinding = DialogMapTypeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set the current selection in the radio group
        val currentMapType = getCurrentMapType()
        when (currentMapType) {
            "Normal" -> dialogBinding.normalMapRadio.isChecked = true
            "Hybrid" -> dialogBinding.hybridMapRadio.isChecked = true
            "Satellite" -> dialogBinding.satelliteMapRadio.isChecked = true
            "Terrain" -> dialogBinding.terrainMapRadio.isChecked = true
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.confirmButton.setOnClickListener {
            // Find which radio button is checked
            val selectedId = dialogBinding.mapTypeRadioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val radioButton = dialog.findViewById<RadioButton>(selectedId)
                val selectedType = radioButton.text.toString()
                saveMapType(selectedType)
                binding.currentMapTypeTextView.text = selectedType
                ToastHelper.showShortToast(context, "Map type updated to $selectedType")
            }
            dialog.dismiss()
        }

        // Set dialog width to match parent
        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.show()
    }

    private fun showLogoutDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogBinding = DialogLogoutBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                userRepository.clearUserData()
                authRepository.logout()
                findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
                ToastHelper.showShortToast(context, "Logged out successfully")
                dialog.dismiss()
            }
        }

        // Set dialog width to match parent
        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.show()
    }

    private fun loadCurrentMapType() {
        // Load the current map type from preferences or data storage
        val defaultType = "Normal" // Set a default value
        val savedType = defaultType // Replace with actual retrieval logic
        binding.currentMapTypeTextView.text = savedType
    }

    private fun getCurrentMapType(): String {
        // Retrieve the current map type
        return binding.currentMapTypeTextView.text.toString()
    }

    private fun saveMapType(mapType: String) {
        // Save the selected map type to preferences or data storage
        // Example with SharedPreferences:
        // val sharedPreferences = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        // sharedPreferences.edit().putString("map_type", mapType).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}