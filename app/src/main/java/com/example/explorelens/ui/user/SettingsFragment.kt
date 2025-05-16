package com.example.explorelens.ui.user

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.UserRepository
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


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root

        userRepository = UserRepository(requireContext())
        authRepository = AuthRepository(requireContext())

        binding.backButton.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileFragment)
        }

        binding.editUserRow.setOnClickListener {
            // Navigate to edit user screen
            // Example: startActivity(Intent(requireContext(), EditUserActivity::class.java))
        }

        binding.changePasswordRow.setOnClickListener {
            // Navigate to change password screen
            // Example: startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        binding.deleteUserRow.setOnClickListener {
            showDeleteUserDialog()
        }

        binding.resetHistoryRow.setOnClickListener {
            showResetHistoryDialog()
        }

        binding.changeMapTypeRow.setOnClickListener {
            showMapTypeDialog()
        }

        binding.logoutRow.setOnClickListener {
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
            // Perform delete account action
            // Example: viewModel.deleteAccount()
            ToastHelper.showShortToast(context, "Account deleted");
            dialog.dismiss()
        }

        // Set dialog width to match parent
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
            // Implement your logic to clear site history
            // Example: MyAppDataBase.getInstance(requireContext()).historyDao().deleteAll()
            ToastHelper.showShortToast(context, "Site history reset");
            dialog.dismiss()
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
                ToastHelper.showShortToast(context, "Map type updated to $selectedType");
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