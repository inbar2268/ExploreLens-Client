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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.adapters.siteHistory.SiteHistoryAdapter
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.databinding.FragmentSiteHistoryBinding
import com.example.explorelens.utils.GeoLocationUtils
import com.example.explorelens.utils.LoadingManager.showLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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
        fetchSiteHistoryFromServer(getCurrentUserId())
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
        val siteRepository = SiteDetailsRepository(requireContext())

        adapter = SiteHistoryAdapter(
            onItemClick = { siteHistory ->
                navigateToSiteDetails(siteHistory)
            },
            siteRepository = siteRepository,
            lifecycleOwner = viewLifecycleOwner
        )

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

        viewModel.getSiteHistoryByUserId(userId).observe(viewLifecycleOwner) { historyList ->
            Log.d(TAG, "Received ${historyList.size} history items from Room")
            val filteredList = historyList.filter { it.userId == userId }
            val uniqueSites = filteredList.groupBy { it.siteInfoId }
                .map { entry -> entry.value.maxByOrNull { it.createdAt }!! }
                .sortedByDescending { it.createdAt }

            if (uniqueSites.isEmpty()) {
                binding.emptyStateView.visibility = View.VISIBLE
                binding.recyclerViewHistory.visibility = View.GONE
            } else {
                binding.emptyStateView.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
                adapter.submitList(uniqueSites)
            }
        }
    }

    private fun fetchSiteHistoryFromServer(userId: String?) {
        if (userId == null || isSyncing) {
            Log.d(TAG, "Cannot fetch history: User ID is null or already syncing")
            return
        }

        Log.d(TAG, "Fetching site history from server")
        showLoading(true)
        isSyncing = true

        lifecycleScope.launch {
            try {
                viewModel.syncSiteHistory(userId)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    isSyncing = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing site history from server", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    isSyncing = false
                    Toast.makeText(requireContext(), "Failed to update history", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchSiteHistoryFromServer(getCurrentUserId())
            binding.swipeRefreshLayout.isRefreshing = false
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
                userId = userId,
                geohash = "u09tvw",
                latitude = 48.8584,
                longitude = 2.2945,
                createdAt = currentTime - (3 * 24 * 60 * 60 * 1000)
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "SagradaFamilia",
                userId = userId,
                geohash = "sp3e3k",
                latitude = 41.4036,
                longitude = 2.1744,
                createdAt = currentTime - (12 * 60 * 60 * 1000)
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "BigBen",
                userId = userId,
                geohash = "gcpvj0",
                latitude = 51.5007,
                longitude = -0.1246,
                createdAt = currentTime - (1 * 24 * 60 * 60 * 1000)
            ),
            SiteHistory(
                id = UUID.randomUUID().toString(),
                siteInfoId = "Colosseum",
                userId = userId,
                geohash = "sr2yd0",
                latitude = 41.8902,
                longitude = 12.4922,
                createdAt = currentTime - (2 * 24 * 60 * 60 * 1000)
            )
        )
    }

    private fun navigateToSiteDetails(siteHistory: SiteHistory) {
        Log.d(TAG, "Navigating to details for site: ${siteHistory.siteInfoId}")
        val bundle = Bundle().apply {
            putString("LABEL_KEY", siteHistory.siteInfoId)
        }
        try {
            findNavController().navigate(
                R.id.action_siteHistoryFragment_to_siteDetailsFragment,
                bundle
            )
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed, using fragment transaction", e)
            val siteDetailsFragment = SiteDetailsFragment().apply {
                arguments = bundle
            }
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
        lifecycleScope.launch {
            val user = userRepository.getUserFromDb()
            if (user != null) {
                if (!user.profilePictureUrl.isNullOrEmpty()) {
                    Glide.with(requireContext())
                        .load(user.profilePictureUrl)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(binding.profileImageView)
                }
            } else {
                userRepository.fetchAndSaveUser()
            }
        }
    }
}