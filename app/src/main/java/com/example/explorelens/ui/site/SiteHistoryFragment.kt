package com.example.explorelens.ui.site

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.explorelens.adapters.siteHistory.SiteHistoryAdapter
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.databinding.FragmentSiteHistoryBinding
import com.example.explorelens.utils.GeoLocationUtils
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.data.db.siteHistory.SiteHistory

class SiteHistoryFragment : Fragment() {

    private var _binding: FragmentSiteHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SiteHistoryViewModel
    private lateinit var adapter: SiteHistoryAdapter

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

        setupViewModel()
        setupRecyclerView()
        loadSiteHistory()
    }

    override fun onResume() {
        super.onResume()

        setupViewModel()
        setupRecyclerView()
        loadSiteHistory()


    }

    private fun setupViewModel() {
        val database = AppDatabase.getInstance(requireContext())

        val siteRepository = SiteHistoryRepository(requireContext())
        val geoLocationUtils = GeoLocationUtils(requireContext()) // יצירת geoLocationUtils

        viewModel = ViewModelProvider(
            this,
            SiteHistoryViewModel.Factory(siteRepository, geoLocationUtils) // העברה לפקטורי
        )[SiteHistoryViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = SiteHistoryAdapter { siteHistory ->
            handleSiteHistoryClick(siteHistory)
        }

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter
    }

    private fun loadSiteHistory() {
        val userId = getCurrentUserId()

        viewModel.getSiteHistoryByUserId(userId).observe(viewLifecycleOwner) { history ->
            if (history.isEmpty()) {
                binding.emptyStateView.visibility = View.VISIBLE
                binding.recyclerViewHistory.visibility = View.GONE
            } else {
                binding.emptyStateView.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
                adapter.submitList(history)
            }
        }

        viewModel.syncSiteHistory(userId)
    }

    private fun handleSiteHistoryClick(siteHistory: SiteHistory) {
        val bundle = Bundle().apply {
            putString("siteId", siteHistory.siteInfoId)
        }
        // אם תשתמשי ב-Navigation Component אפשר לפתוח את זה
        // findNavController().navigate(R.id.action_historyFragment_to_siteDetailsFragment, bundle)
    }

    private fun getCurrentUserId(): String {
        // פה את צריכה לממש לפי המערכת שלך (SharedPreferences, Firebase וכו')
//        return "current_user_id"
        return "68027184e7ceef90b0ac46f9"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
