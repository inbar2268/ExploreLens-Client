package com.example.explorelens.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.ui.site.SiteDetailsFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment() {

    private val TAG = "MapFragment"
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var siteHistoryRepository: SiteHistoryRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        // Initialize repositories
        authTokenManager = AuthTokenManager.getInstance(requireContext())
        siteHistoryRepository = SiteHistoryRepository(requireContext())

        setupMap(savedInstanceState)

        return view
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map

            // Default initial location (can be anywhere)
            val defaultLocation = LatLng(31.7683, 35.2137) // Jerusalem as fallback
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 8f))

            // Load user's visited sites
            loadUserVisitedSites()
        }
    }

    private fun loadUserVisitedSites() {
        val userId = authTokenManager.getUserId()
        if (userId == null) {
            Log.e(TAG, "No user ID available")
            return
        }

        // Observe site history data
        siteHistoryRepository.getSiteHistoryByUserId(userId).observe(viewLifecycleOwner) { historyList ->
            // Filter for current user
            val filteredList = historyList.filter { it.userId == userId }

            // Group by siteInfoId to avoid duplicates
            val uniqueSites = filteredList.groupBy { it.siteInfoId }
                .map { entry -> entry.value.maxByOrNull { it.createdAt }!! }

            Log.d(TAG, "Found ${uniqueSites.size} unique visited sites")

            // Add markers for each location
            addMarkersForVisitedSites(uniqueSites)
        }

        // Sync with server to ensure we have the latest data
        lifecycleScope.launch {
            siteHistoryRepository.syncSiteHistory(userId)
        }
    }

    private fun addMarkersForVisitedSites(sites: List<SiteHistory>) {
        if (sites.isEmpty()) {
            Log.d(TAG, "No sites to display on map")
            return
        }

        // Clear existing markers
        googleMap.clear()

        // Track bounds to fit all markers
        var firstLocation: LatLng? = null

        // Add a marker for each site
        sites.forEach { site ->
            // Use the actual coordinates from the SiteHistory
            val location = LatLng(site.latitude, site.longitude)

            // Remember first location to move camera
            if (firstLocation == null) {
                firstLocation = location
            }

            // Add marker with site info
            googleMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(formatSiteId(site.siteInfoId))
                    .snippet("Visited: ${formatDate(site.createdAt)}")
            )?.tag = site.siteInfoId // Store siteInfoId as the marker's tag

            Log.d(TAG, "Added marker for ${site.siteInfoId} at ${site.latitude}, ${site.longitude}")
        }

        // Move camera to first location with reasonable zoom
        firstLocation?.let {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 10f))
        }

        // Set up marker click listener
        googleMap.setOnInfoWindowClickListener { marker ->
            val siteInfoId = marker.tag as? String
            if (siteInfoId != null) {
                Log.d(TAG, "Marker clicked for site: $siteInfoId")
                navigateToSiteDetails(siteInfoId)
            }
        }
    }

    private fun formatSiteId(siteInfoId: String): String {
        // Format the site ID to be more readable
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return format.format(date)
    }

    private fun navigateToSiteDetails(siteInfoId: String) {
        // Create bundle with site information
        val bundle = Bundle().apply {
            putString("LABEL_KEY", siteInfoId)
        }

        try {
            // Try to use Navigation Component
            findNavController().navigate(
                R.id.action_mapFragment_to_siteDetailsFragment,
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}