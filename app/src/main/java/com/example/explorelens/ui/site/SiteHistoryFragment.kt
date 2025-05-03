package com.example.explorelens.ui.site

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class SiteHistoryFragment : Fragment() {
    private val TAG = "SiteHistoryFragment"

    private var _binding: FragmentSiteHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SiteHistoryViewModel
    private lateinit var adapter: SiteHistoryAdapter
    private lateinit var authTokenManager: AuthTokenManager

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

        authTokenManager = AuthTokenManager.getInstance(requireContext())
        setupViewModel()
        setupRecyclerView()
        observeData()
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

    private fun observeData() {
        // Get the current user ID
        val userId = getCurrentUserId() ?: return

        // First, trigger sync with the server to ensure we have the latest data
        viewModel.syncSiteHistory(userId)

        // Observe loading state
        viewModel.loading?.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe site history data
        viewModel.getSiteHistoryByUserId(userId).observe(viewLifecycleOwner) { historyList ->
            Log.d(TAG, "Received ${historyList.size} history items")

            if (historyList.isEmpty()) {
                // No data from Room or server, show mock data
                Log.d(TAG, "No history data, showing mock data")
                showMockData()
            } else {
                // Real data available, display it
                Log.d(TAG, "Displaying real history data")
                showHistoryData(historyList)
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

        // Use this mock data instead of the ImageAnalyzedResult data
        return listOf(
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "EiffelTower",
                userId = userId,
                geohash = "u09tvw",
                latitude = 48.8584,
                longitude = 2.2945,
                createdAt = currentTime - (3 * 24 * 60 * 60 * 1000) // 3 days ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "SagradaFamilia",
                userId = userId,
                geohash = "sp3e3k",
                latitude = 41.4036,
                longitude = 2.1744,
                createdAt = currentTime - (12 * 60 * 60 * 1000) // 12 hours ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "BigBen",
                userId = userId,
                geohash = "gcpvj0",
                latitude = 51.5007,
                longitude = -0.1246,
                createdAt = currentTime - (1 * 24 * 60 * 60 * 1000) // 1 day ago
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "Colosseum",
                userId = userId,
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
}