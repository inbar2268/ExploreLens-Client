package com.example.explorelens.ui.site

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.explorelens.R
import com.example.explorelens.adapters.siteHistory.SiteHistoryAdapter
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.databinding.FragmentSiteHistoryBinding
import com.example.explorelens.utils.GeoLocationUtils
import java.util.UUID
import com.bumptech.glide.Glide
import com.example.explorelens.data.repository.UserRepository
import androidx.lifecycle.lifecycleScope
import com.example.explorelens.utils.LoadingManager.showLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SiteHistoryFragment : Fragment() {
    private val TAG = "SiteHistoryFragment"

    private var _binding: FragmentSiteHistoryBinding? = null
    private val binding get() = _binding!!

    private var progressBar: ProgressBar? = null

    private lateinit var viewModel: SiteHistoryViewModel
    private lateinit var adapter: SiteHistoryAdapter
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var userRepository: UserRepository

    private var isSyncing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSiteHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = view.findViewById(R.id.progressBar)

        userRepository = UserRepository(requireContext())

        authTokenManager = AuthTokenManager.getInstance(requireContext())
        setupViewModel()
        setupRecyclerView()
        loadUserProfile()
        syncAndObserveData()
        setupSwipeToRefresh()
    }

    private fun setupViewModel() {
        val siteRepository = SiteHistoryRepository(requireContext())
        val geoLocationUtils = GeoLocationUtils(requireContext())

        viewModel = ViewModelProvider(
            this,
            SiteHistoryViewModel.Factory(siteRepository, geoLocationUtils)
        )[SiteHistoryViewModel::class.java]
    }

    private fun setupRecyclerView() {
        // Initialize adapter with click listener
        adapter = SiteHistoryAdapter { siteHistory ->
            navigateToSiteDetails(siteHistory)
        }

        // Set up grid layout with 2 columns
        binding.recyclerViewHistory.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@SiteHistoryFragment.adapter
        }
    }

    private fun syncAndObserveData() {
        val userId = getCurrentUserId()
        if (userId == null) {
            Log.e(TAG, "No user ID available, showing mock data")
            showMockData()
            return
        }

        // Show loading indicator
        showLoading(true)
        isSyncing = true

        // Start sync in background
        lifecycleScope.launch {
            try {
                // Call sync function
                viewModel.syncSiteHistory(userId)

                // After sync completes, start observing data from Room
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    showLoading(false)
                    observeData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync", e)
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    showLoading(false)
                    // Show error message
                    Toast.makeText(requireContext(), "Sync failed, showing cached data", Toast.LENGTH_SHORT).show()
                    observeData()
                }
            }
        }
    }

    private fun observeData() {
        val userId = getCurrentUserId()
        if (userId == null) {
            Log.e(TAG, "No user ID available, showing mock data")
            showMockData()
            return
        }

        Log.d(TAG, "Loading history for user ID: $userId")

        // Just observe the data from Room without syncing
        viewModel.getSiteHistoryByUserId(userId).observe(viewLifecycleOwner) { historyList ->
            Log.d(TAG, "Received ${historyList.size} history items")

            // Additional filter to ensure we only show the current user's history
            val filteredList = historyList.filter { it.userId == userId }
            Log.d(TAG, "After filtering for current user: ${filteredList.size} items")

            // Group by siteInfoId to avoid duplicates
            val uniqueSites = filteredList.groupBy { it.siteInfoId }
                .map { entry -> entry.value.maxByOrNull { it.createdAt }!! }
                .sortedByDescending { it.createdAt }

            Log.d(TAG, "Unique sites after grouping: ${uniqueSites.size}")

            // Update history count
            binding.historyCountTextView.text = "${uniqueSites.size} unique sites visited"

            if (uniqueSites.isEmpty()) {
                // No data for current user, show mock data
                Log.d(TAG, "No history data for current user, showing mock data")
                showMockData()
            } else {
                // Real data available, display it
                Log.d(TAG, "Displaying real history data for current user (${uniqueSites.size} sites)")
                showHistoryData(uniqueSites)
            }
        }
    }

    private fun showHistoryData(historyList: List<SiteHistory>) {
        binding.emptyStateView.visibility = View.GONE
        binding.recyclerViewHistory.visibility = View.VISIBLE

        // Sort by date descending (newest first)
        val sortedList = historyList.sortedByDescending { it.createdAt }
        adapter.submitList(sortedList)
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            val userId = getCurrentUserId()
            if (userId != null && !isSyncing) {
                Log.d(TAG, "User pulled to refresh, syncing site history")

                // Show refreshing
                isSyncing = true

                // Launch sync in coroutine
                lifecycleScope.launch {
                    try {
                        viewModel.syncSiteHistory(userId)
                        withContext(Dispatchers.Main) {
                            // Data will refresh automatically via LiveData
                            binding.swipeRefreshLayout.isRefreshing = false
                            isSyncing = false
                            // Optional: show success message
                            Toast.makeText(requireContext(), "History updated", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing on refresh", e)
                        withContext(Dispatchers.Main) {
                            binding.swipeRefreshLayout.isRefreshing = false
                            isSyncing = false
                            // Show error message
                            Toast.makeText(requireContext(), "Failed to sync", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Stop animation if already syncing
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showMockData() {
        val mockData = createMockHistoryData()

        if (mockData.isNotEmpty()) {
            binding.emptyStateView.visibility = View.GONE
            binding.recyclerViewHistory.visibility = View.VISIBLE
            adapter.submitList(mockData)
        } else {
            binding.emptyStateView.visibility = View.VISIBLE
            binding.recyclerViewHistory.visibility = View.GONE
        }
    }

    private fun createMockHistoryData(): List<SiteHistory> {
        val currentTime = System.currentTimeMillis()
        val userId = getCurrentUserId() ?: "mock_user"

        return listOf(
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "EiffelTower",
                userId = userId, // Use current user ID for mock data
                geohash = "u09tvw",
                latitude = 48.8584,
                longitude = 2.2945,
                createdAt = currentTime - (3 * 24 * 60 * 60 * 1000) // 3 days ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "SagradaFamilia",
                userId = userId, // Use current user ID for mock data
                geohash = "sp3e3k",
                latitude = 41.4036,
                longitude = 2.1744,
                createdAt = currentTime - (12 * 60 * 60 * 1000) // 12 hours ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "BigBen",
                userId = userId, // Use current user ID for mock data
                geohash = "gcpvj0",
                latitude = 51.5007,
                longitude = -0.1246,
                createdAt = currentTime - (1 * 24 * 60 * 60 * 1000) // 1 day ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "Colosseum",
                userId = userId, // Use current user ID for mock data
                geohash = "sr2yd0",
                latitude = 41.8902,
                longitude = 12.4922,
                createdAt = currentTime - (2 * 24 * 60 * 60 * 1000) // 2 days ago
            )
        )
    }

    private fun navigateToSiteDetails(siteHistory: SiteHistory) {
        Log.d(TAG, "Navigating to details for site: ${siteHistory.siteInfoId}")

        // Create bundle with site information
        val bundle = Bundle().apply {
            putString("LABEL_KEY", siteHistory.siteInfoId)
        }

        try {
            // Try to use Navigation Component
            findNavController().navigate(
                R.id.action_siteHistoryFragment_to_siteDetailsFragment,
                bundle
            )
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed, using fragment transaction", e)

            // Fallback to traditional fragment transaction
            val siteDetailsFragment = SiteDetailsFragment().apply {
                arguments = bundle
            }

            // Find the correct container ID in your layout
            val containerId = (view?.parent as? ViewGroup)?.id ?: R.id.nav_host_fragment

            parentFragmentManager.beginTransaction()
                .replace(containerId, siteDetailsFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun getCurrentUserId(): String? {
        return authTokenManager.getUserId()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun loadUserProfile() {
        val userId = getCurrentUserId() ?: return

        // Set default values while loading
        binding.usernameTextView.text = "Explorer"

        // Load user profile data
        lifecycleScope.launch {
            val user = userRepository.getUserFromDb()
            if (user != null) {
                // Set username
                binding.usernameTextView.text = user.username ?: "Explorer"

                // Load profile image
                if (!user.profilePictureUrl.isNullOrEmpty()) {
                    Glide.with(requireContext())
                        .load(user.profilePictureUrl)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(binding.profileImageView)
                }
            }
        } ?: run {
            // If user not found in database, try to fetch from server
            lifecycleScope.launch {
                userRepository.fetchAndSaveUser()
            }
        }
    }



}