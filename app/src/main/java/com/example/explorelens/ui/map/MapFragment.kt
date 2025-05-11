package com.example.explorelens.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.explorelens.R
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewHolder
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.databinding.ItemSiteHistoryBinding
import com.example.explorelens.ui.site.SiteDetailsFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    // Current bottom sheet dialog
    private var bottomSheetDialog: BottomSheetDialog? = null

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
                showSiteBottomSheet(marker)
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

    // In MapFragment.kt - update the addMarkersForVisitedSites method
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

    private fun loadSiteDetails(siteMarker: SiteMarker) {
        val siteId = siteMarker.siteId

        // Fetch site details
        siteDetailsRepository.getSiteDetails(siteId).observe(viewLifecycleOwner) { siteDetails ->
            if (siteDetails != null) {
                // Update the site marker with details
                val updatedMarker = siteMarker.copy(siteDetails = siteDetails)
                val index = siteMarkers.indexOf(siteMarker)
                if (index >= 0) {
                    siteMarkers[index] = updatedMarker
                }

                // Update marker title
                siteMarker.marker?.title = siteDetails.name ?: siteMarker.name

                // If bottom sheet is showing this marker, update it
                bottomSheetDialog?.let { dialog ->
                    val sheetView = dialog.findViewById<View>(R.id.siteNameTextView)?.rootView
                    if (sheetView != null) {
                        val currentSiteId = sheetView.tag as? String
                        if (currentSiteId == siteId) {
                            updateBottomSheetContent(sheetView, updatedMarker)
                        }
                    }
                }
            }
        }
    }

    private fun showSiteBottomSheet(marker: Marker) {
        // Find the site marker
        val siteMarker = when (val tag = marker.tag) {
            is SiteMarker -> tag
            else -> {
                Log.e(TAG, "Invalid marker tag: $tag")
                return
            }
        }

        Log.d(TAG, "Showing bottom sheet for site: ${siteMarker.siteId}")

        // Close existing dialog if open
        bottomSheetDialog?.dismiss()

        // Create bottom sheet dialog
        bottomSheetDialog = BottomSheetDialog(requireContext()).apply {
            val view = layoutInflater.inflate(R.layout.marker_bottom_sheet, null)
            view.tag = siteMarker.siteId

            setContentView(view)

            // Set the name and date
            val siteNameTextView = view.findViewById<TextView>(R.id.siteNameTextView)
            val visitDateTextView = view.findViewById<TextView>(R.id.visitDateTextView)
            val siteImageView = view.findViewById<ShapeableImageView>(R.id.siteImageView)

            // Set initial values
            siteNameTextView.text = siteMarker.name
            visitDateTextView.text = siteMarker.visitDate

            // Load image directly using the method that matches your SiteHistoryViewHolder approach
            loadImageDirectly(siteMarker.siteId, siteImageView)

            // Set button click listener
            view.findViewById<Button>(R.id.viewDetailsButton).setOnClickListener {
                navigateToSiteDetails(siteMarker.siteId)
                dismiss()
            }

            show()
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

                            // Update the site name in the bottom sheet
                            val bottomSheet = bottomSheetDialog?.findViewById<View>(R.id.siteNameTextView)?.rootView
                            bottomSheet?.findViewById<TextView>(R.id.siteNameTextView)?.text = siteDetails.name

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

    private fun updateBottomSheetContent(view: View, siteMarker: SiteMarker) {
        val siteImageView = view.findViewById<ShapeableImageView>(R.id.siteImageView)
        val siteNameTextView = view.findViewById<TextView>(R.id.siteNameTextView)
        val visitDateTextView = view.findViewById<TextView>(R.id.visitDateTextView)

        // Set name and date
        siteNameTextView.text = siteMarker.name
        visitDateTextView.text = siteMarker.visitDate

        // Load image
        if (siteMarker.imageUrl != null) {
            Log.d(TAG, "Loading image from URL: ${siteMarker.imageUrl}")
            Glide.with(requireContext())
                .load(siteMarker.imageUrl)
                .placeholder(R.drawable.noimage)
                .error(R.drawable.noimage)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(siteImageView)
        } else {
            Glide.with(requireContext())
                .load(R.drawable.noimage)
                .centerCrop()
                .into(siteImageView)
        }
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
        // Dismiss bottom sheet if open
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
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