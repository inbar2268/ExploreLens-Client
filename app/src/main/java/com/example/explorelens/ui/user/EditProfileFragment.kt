package com.example.explorelens.ui.user

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.db.user.UserEntity
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.databinding.FragmentEditProfileBinding
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    sealed class EditProfileState {
        object Loading : EditProfileState()
        data class Success(val user: UserEntity) : EditProfileState()
        data class Error(val message: String) : EditProfileState()
        object UploadingImage : EditProfileState()
        object Saving : EditProfileState()
        object SaveSuccess : EditProfileState()
        data class SaveError(val message: String) : EditProfileState()
    }

    // ViewModel code now in the Fragment
    private val _editProfileState = MutableLiveData<EditProfileState>()
    val editProfileState: LiveData<EditProfileState> = _editProfileState

    private lateinit var userRepository: UserRepository
    private var currentUser: UserEntity? = null
    private var selectedImageUri: Uri? = null
    private var uploadedImageUrl: String? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                // Load the selected image into the profile image view
                Glide.with(this)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.avatar_placeholder)
                    .error(R.drawable.avatar_placeholder)
                    .into(binding.profileImage)

                uploadedImageUrl = null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repository
        userRepository = UserRepository(requireActivity().application)

        setupObservers()
        setupClickListeners()

        // Load current user data
        loadUserData()
    }

    private fun setupClickListeners() {
        // Profile image click to select new image
        binding.profileImage.setOnClickListener {
            openImagePicker()
        }

        // Save button click
        binding.saveButton.setOnClickListener {
            saveProfileChanges()
        }

        // Back button or cancel functionality (if you have one)
        binding.backButton.setOnClickListener{
            findNavController().navigateUp()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/gif", "image/webp")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.type = "image/*"

        imagePickerLauncher.launch(intent)
    }

    private fun loadUserData() {
        _editProfileState.value = EditProfileState.Loading

        viewLifecycleOwner.lifecycleScope.launch {
            // Get current user from local database
            val user = userRepository.getUserFromDb()

            if (user != null) {
                currentUser = user
                _editProfileState.value = EditProfileState.Success(user)
            } else {
                // If no local user, try to fetch from server
                val result = userRepository.fetchAndSaveUser()
                result.fold(
                    onSuccess = { fetchedUser ->
                        currentUser = fetchedUser
                        _editProfileState.value = EditProfileState.Success(fetchedUser)
                    },
                    onFailure = { exception ->
                        _editProfileState.value = EditProfileState.Error(
                            exception.message ?: "Failed to load user data"
                        )
                    }
                )
            }
        }
    }

    private fun saveProfileChanges() {
        val username = binding.nameEditText.text.toString().trim()

        if (username.isEmpty()) {
            ToastHelper.showShortToast(requireContext(), "Name cannot be empty")
            return
        }

        val currentUserData = currentUser ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Step 1: Upload image if a new one was selected
                if (selectedImageUri != null && uploadedImageUrl == null) {
                    _editProfileState.value = EditProfileState.UploadingImage

                    val uploadResult = userRepository.uploadProfileImage(selectedImageUri!!)
                    uploadResult.fold(
                        onSuccess = { imageUrl ->
                            uploadedImageUrl = imageUrl
                            // Continue with profile update
                            updateUserProfile(currentUserData, username, imageUrl)
                        },
                        onFailure = { exception ->
                            _editProfileState.value = EditProfileState.SaveError(
                                "Failed to upload image: ${exception.message}"
                            )
                            return@launch
                        }
                    )
                } else {
                    // No new image selected, use existing or uploaded URL
                    val imageUrl = uploadedImageUrl ?: currentUserData.profilePictureUrl
                    updateUserProfile(currentUserData, username, imageUrl)
                }
            } catch (e: Exception) {
                _editProfileState.value = EditProfileState.SaveError(
                    "An error occurred: ${e.message}"
                )
            }
        }
    }

    private suspend fun updateUserProfile(currentUserData: UserEntity, username: String, imageUrl: String?) {
        _editProfileState.value = EditProfileState.Saving

        // Create updated user object
        val updatedUser = currentUserData.copy(
            username = username,
            profilePictureUrl = imageUrl
        )

        //Update user profile
        val result = userRepository.updateUser(updatedUser)

        result.fold(
            onSuccess = {
                _editProfileState.value = EditProfileState.SaveSuccess
                ToastHelper.showShortToast(requireContext(), "Profile updated successfully")
                findNavController().navigateUp()
            },
            onFailure = { exception ->
                _editProfileState.value = EditProfileState.SaveError(
                    exception.message ?: "Failed to update profile"
                )
            }
        )
    }

    private fun setupObservers() {
        editProfileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EditProfileState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.saveButton.isEnabled = false
                    binding.nameEditText.isEnabled = false
                }
                is EditProfileState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.isEnabled = true
                    binding.nameEditText.isEnabled = true

                    // Populate UI with user data
                    binding.nameEditText.setText(state.user.username)

                    // Load profile picture
                    if (!state.user.profilePictureUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(state.user.profilePictureUrl)
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.drawable.avatar_placeholder)
                            .error(R.drawable.avatar_placeholder)
                            .into(binding.profileImage)
                    } else {
                        binding.profileImage.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
                is EditProfileState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.isEnabled = false
                    binding.nameEditText.isEnabled = false

                    ToastHelper.showShortToast(requireContext(), state.message)
                }
                is EditProfileState.UploadingImage -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.saveButton.isEnabled = false
                    binding.nameEditText.isEnabled = false
                }
                is EditProfileState.Saving -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.saveButton.isEnabled = false
                    binding.nameEditText.isEnabled = false
                }
                is EditProfileState.SaveSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    // Navigation handled in saveProfileChanges()
                }
                is EditProfileState.SaveError -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.isEnabled = true
                    binding.nameEditText.isEnabled = true

                    ToastHelper.showShortToast(requireContext(), state.message)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}