package com.example.explorelens.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.db.User
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.databinding.FragmentProfileBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // UserState sealed class for handling UI states
    sealed class UserState {
        object Loading : UserState()
        data class Success(val user: User) : UserState()
        data class Error(val message: String) : UserState()
        object Logout : UserState()
    }

    // ViewModel code now in the Fragment
    private val _userState = MutableLiveData<UserState>()
    val userState: LiveData<UserState> = _userState

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setupBottomNavigation() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemReselectedListener { menuItem ->
            if (menuItem.itemId == R.id.profileFragment) {
                val navController = findNavController()
                val currentDestinationId = navController.currentDestination?.id
                if (currentDestinationId != R.id.profileFragment) {
                    navController.popBackStack(R.id.profileFragment, false)
                } else {
                    fetchUserData()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repositories
        userRepository = UserRepository(requireActivity().application)
        authRepository = AuthRepository(requireActivity().application)

        // Initialize user data observer
        setupUserObserver()

        setupObservers()
        setupRefreshListener()
        setupBottomNavigation()

        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }

        // Fetch user data when the fragment is created
        fetchUserData()
    }

    private fun setupUserObserver() {
        viewLifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewLifecycleOwner.lifecycleScope.launch {
                    userRepository.observeUser().collectLatest { user ->
                        if (user != null) {
                            _userState.value = UserState.Success(user)
                        }
                    }
                }
            }
        })
    }

    // Fetch user data from server
    fun fetchUserData() {
        _userState.value = UserState.Loading

        viewLifecycleOwner.lifecycleScope.launch {
            // First, try to get user from local database
            val localUser = userRepository.getUserFromDb()

            if (localUser != null) {
                // If user is found locally, update UI with local data
                _userState.value = UserState.Success(localUser)
            } else {
                // If no user is found locally, fetch user data from server
                val result = userRepository.fetchAndSaveUser()

                result.fold(
                    onSuccess = {
                        // The user data will be updated through the observer once the repository saves it
                    },
                    onFailure = { exception ->
                        // Instead of using Error state, directly use Logout state
                        _userState.value = UserState.Logout

                        // Show toast and navigate to login page when user ID is not found
                        ToastHelper.showShortToast(requireContext(), "User not found. Please login again")
                    }
                )
            }
        }
    }

    private fun setupObservers() {
        userState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UserState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.errorMessage.visibility = View.GONE
                }
                is UserState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.GONE

                    // Update UI with user data
                    binding.usernameText.text = state.user.username
                    binding.emailText.text = state.user.email

                    // Load profile picture with Glide
                    if (!state.user.profilePictureUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(state.user.profilePictureUrl)
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.drawable.ic_default_profile)
                            .error(R.drawable.ic_default_profile)
                            .into(binding.profileImage)
                    } else {
                        binding.profileImage.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
                is UserState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.VISIBLE
                    binding.errorMessage.text = state.message

                    ToastHelper.showShortToast(context, state.message)
                }
                is UserState.Logout -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.GONE
                    findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                }
            }
        }
    }

    private fun setupRefreshListener() {
        binding.swipeRefresh.setOnRefreshListener {
            fetchUserData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}