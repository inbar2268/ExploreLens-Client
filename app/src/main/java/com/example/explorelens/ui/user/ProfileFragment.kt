package com.example.explorelens.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.databinding.FragmentProfileBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileFragment : Fragment(), WorldMapManager.MapClickListener {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private lateinit var uiHelper: ProfileUIHelper
    private lateinit var mapManager: WorldMapManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeComponents()
        setupObservers()
        setupListeners()
        setupBottomNavigation()

        // Load initial data
        viewModel.fetchUserData()
        // Statistics are automatically loaded via repository LiveData
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        uiHelper = ProfileUIHelper(binding)
        mapManager = WorldMapManager(requireContext(), binding.worldMapWebView)

        mapManager.setMapClickListener(this)
        mapManager.setupWorldMap {
            // Map is ready - statistics will update automatically via LiveData
        }
    }

    private fun setupObservers() {
        // User state observer
        viewModel.userState.observe(viewLifecycleOwner) { state ->
            _binding?.let {
                when (state) {
                    is ProfileViewModel.UserState.Loading -> {
                        uiHelper.showLoading()
                    }
                    is ProfileViewModel.UserState.Success -> {
                        uiHelper.updateUserInfo(state.user, this)
                    }
                    is ProfileViewModel.UserState.Error -> {
                        uiHelper.showError(state.message)
                        ToastHelper.showShortToast(context, state.message)
                    }
                    is ProfileViewModel.UserState.Logout -> {
                        uiHelper.hideLoading()
                        uiHelper.hideError()
                        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                    }
                }
            }
        }

        // Statistics state observer (now reactive from repository)
        viewModel.statisticsState.observe(viewLifecycleOwner) { state ->
            _binding?.let {
                when (state) {
                    is ProfileViewModel.StatisticsState.Loading -> {
                        uiHelper.showStatisticsLoading()
                    }
                    is ProfileViewModel.StatisticsState.Success -> {
                        uiHelper.updateStatistics(state.percentage, state.countryCount)
                        // Always try to update map, even if not ready yet (will be queued)
                        mapManager.updateCountries(state.countries)

                        // Optionally show a subtle indicator if data is from cache
                        if (state.isFromCache) {
                            // You could show a small "cached" indicator if desired
                            // uiHelper.showCacheIndicator()
                        }
                    }
                    is ProfileViewModel.StatisticsState.Error -> {
                        uiHelper.showStatisticsError()
                        if (_binding != null) {
                            ToastHelper.showShortToast(requireContext(), state.message)
                        }
                    }
                }
            }
        }

        // Refresh state observer
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            _binding?.let {
                uiHelper.setRefreshing(isRefreshing)
            }
        }
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAllData()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemReselectedListener { menuItem ->
            if (menuItem.itemId == R.id.profileFragment) {
                if (isAdded) {
                    val navController = findNavController()
                    val currentDestinationId = navController.currentDestination?.id
                    if (currentDestinationId != R.id.profileFragment) {
                        navController.popBackStack(R.id.profileFragment, false)
                    } else {
                        // Refresh data when user taps profile tab again
                        viewModel.refreshAllData()
                    }
                }
            }
        }
    }

    override fun onCountryClicked(countryId: String) {
        // Handle country click - you can add navigation or other actions here
        // For now, the toast is handled in WorldMapManager
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapManager.cleanup() // Clean up map state
        _binding = null
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemReselectedListener(null)
    }
}