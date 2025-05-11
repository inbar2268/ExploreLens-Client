package com.example.explorelens.ui.map

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.ui.site.SiteDetailsFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private val TAG = "MapFragment"
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var siteHistoryRepository: SiteHistoryRepository
    private lateinit var siteDetailsRepository: SiteDetailsRepository

    // Store all site markers
    private val siteMarkers = mutableListOf<SiteMarker>()

    // Current popup dialog
    private var popupDialog: AlertDialog? = null

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
        siteDetailsRepository = SiteDetailsRepository(requireContext())

        setupMap(savedInstanceState)

        return view
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map

            // Set click listener for markers
            googleMap.setOnMarkerClickListener { marker ->
                showSitePopupDialog(marker)
                true // Return true to consume the event
            }

            // Default initial location
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

        // Sync site history first
        lifecycleScope.launch {
            try {
                // Synchronize site history with server before loading
                siteHistoryRepository.syncSiteHistory(userId)

                // Observe site history data after sync
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
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing and loading site history", e)
            }
        }
    }

    private fun addMarkersForVisitedSites(sites: List<SiteHistory>) {
        if (sites.isEmpty()) {
            Log.d(TAG, "No sites to display on map")
            return
        }

        // Clear existing markers
        googleMap.clear()
        siteMarkers.clear()

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

            // Format site ID to be more readable initially
            val formattedSiteId = site.siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            // Create initial marker with formatted site ID
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(formattedSiteId) // Use formatted ID until we get the real name
            )

            // Create and store site marker
            val siteMarker = SiteMarker(
                siteHistory = site,
                latLng = location,
                marker = marker
            )
            siteMarkers.add(siteMarker)

            // Store reference in marker tag
            marker?.tag = siteMarker

            // Preload site details to update marker titles
            val cleanSiteId = site.siteInfoId.replace(" ", "")
            ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
                .enqueue(object : retrofit2.Callback<com.example.explorelens.data.model.SiteDetails.SiteDetails> {
                    override fun onResponse(
                        call: retrofit2.Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                        response: retrofit2.Response<com.example.explorelens.data.model.SiteDetails.SiteDetails>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            val siteDetails = response.body()
                            if (!siteDetails?.name.isNullOrEmpty()) {
                                // Update marker title
                                marker?.title = siteDetails?.name
                            }
                        }
                    }

                    override fun onFailure(
                        call: retrofit2.Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                        t: Throwable
                    ) {
                        Log.e(TAG, "Error fetching site details for marker title", t)
                    }
                })
        }

        // Move camera to first location with reasonable zoom
        firstLocation?.let {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 10f))
        }
    }

    private fun showSitePopupDialog(marker: Marker) {
        // Find the site marker
        val siteMarker = when (val tag = marker.tag) {
            is SiteMarker -> tag
            else -> {
                Log.e(TAG, "Invalid marker tag: $tag")
                return
            }
        }

        Log.d(TAG, "Showing popup dialog for site: ${siteMarker.siteId}")

        // Close existing dialog if open
        popupDialog?.dismiss()

        // Create custom popup dialog
        val dialogView = layoutInflater.inflate(R.layout.marker_popup_dialog, null)

        // Set up the dialog views
        val siteNameTextView = dialogView.findViewById<TextView>(R.id.siteNameTextView)
        val visitDateTextView = dialogView.findViewById<TextView>(R.id.visitDateTextView)
        val siteImageView = dialogView.findViewById<ShapeableImageView>(R.id.siteImageView)
        val viewDetailsButton = dialogView.findViewById<Button>(R.id.viewDetailsButton)
        val closeButton = dialogView.findViewById<ImageView>(R.id.closeButton)

        // Set initial values
        siteNameTextView.text = siteMarker.name
        visitDateTextView.text = siteMarker.visitDate

        // Load the image
        loadImageDirectly(siteMarker.siteId, siteImageView)

        // Create and show the dialog
        popupDialog = AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set click listeners
        viewDetailsButton.setOnClickListener {
            navigateToSiteDetails(siteMarker.siteId)
            popupDialog?.dismiss()
        }

        closeButton.setOnClickListener {
            popupDialog?.dismiss()
        }

        // Show dialog
        popupDialog?.show()

        // Optional: Set window properties for better appearance
        popupDialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Center the dialog
            val params = window.attributes
            params.gravity = Gravity.CENTER
            window.attributes = params
        }
    }

    private fun loadImageDirectly(siteId: String, imageView: ShapeableImageView) {
        val cleanSiteId = siteId.replace(" ", "")
        Log.d(TAG, "Directly loading image for site: $cleanSiteId")

        // Set a default placeholder while loading
        imageView.setImageResource(R.drawable.noimage)

        // Use the API directly like in your SiteHistoryViewHolder
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
            .enqueue(object : retrofit2.Callback<com.example.explorelens.data.model.SiteDetails.SiteDetails> {
                override fun onResponse(
                    call: retrofit2.Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    response: retrofit2.Response<com.example.explorelens.data.model.SiteDetails.SiteDetails>
                ) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()

                        // Check if we have a valid site details object
                        if (siteDetails != null) {
                            Log.d(TAG, "Received site details: ${siteDetails.name}")
                            Log.d(TAG, "Image URL: ${siteDetails.imageUrl}")

                            // Update the site name in the dialog
                            val dialog = popupDialog
                            if (dialog != null && dialog.isShowing) {
                                dialog.findViewById<TextView>(R.id.siteNameTextView)?.text = siteDetails.name
                            }

                            // Load image if available
                            if (!siteDetails.imageUrl.isNullOrEmpty()) {
                                try {
                                    if (isAdded) { // Check if fragment is still attached
                                        Glide.with(requireContext())
                                            .load(siteDetails.imageUrl)
                                            .placeholder(R.drawable.noimage)
                                            .error(R.drawable.noimage)
                                            .centerCrop()
                                            .transition(DrawableTransitionOptions.withCrossFade())
                                            .into(imageView)

                                        Log.d(TAG, "Successfully started loading image")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error loading image: ${e.message}", e)
                                }
                            } else {
                                Log.d(TAG, "No image URL in response")
                            }
                        } else {
                            Log.e(TAG, "Site details is null")
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch site details: ${response.code()}")
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    t: Throwable
                ) {
                    Log.e(TAG, "Error fetching site details", t)
                }
            })
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

            val containerId = (view?.parent as? ViewGroup)?.id ?: R.id.nav_host_fragment

            parentFragmentManager.beginTransaction()
                .replace(containerId, siteDetailsFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    // Standard MapView lifecycle methods
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
        // Dismiss popup dialog if open
        popupDialog?.dismiss()
        popupDialog = null
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

// Data class for site markers
