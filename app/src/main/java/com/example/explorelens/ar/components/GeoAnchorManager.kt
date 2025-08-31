package com.example.explorelens.ar.components

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.example.explorelens.ArActivity
import com.example.explorelens.R
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.ar.render.FilterListManager
import com.example.explorelens.data.model.PointOfIntrests.GeoLocation
import com.example.explorelens.data.model.PointOfIntrests.OpeningHours
import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import com.example.explorelens.data.repository.NearbyPlacesRepository
import com.example.explorelens.ui.places.LayerDetailFragment
import com.example.explorelens.utils.GeoLocationUtils
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class GeoAnchorManager(
    private val activity: ArActivity,
    private val view: ArActivityView,
    private val geoLocationUtils: GeoLocationUtils,
    private val arSceneRenderer: ARSceneRenderer,
    private val networkScope: CoroutineScope
) {
    companion object {
        private const val TAG = "GeoAnchorManager"
        private const val LOCATION_UPDATE_INTERVAL = 30_000L // 30 seconds
    }

    // Caching for Location
    private val locationCache = mutableMapOf<String, Location>()
    private var lastLocationUpdate = 0L
    private var lastFetchedLocation: Location? = null
    private val DISTANCE_THRESHOLD_METERS = 450.0
    private val DISTANCE_CHECK_INTERVAL = 30_000L

    // State management
    private var shouldPlaceGeoAnchors = false
    private var pendingPlaces: List<PointOfInterest>? = null

    // Use singleton repository instance
    private val nearbyPlacesRepository = NearbyPlacesRepository.getInstance(activity)

    interface GeoAnchorCallback {
        fun onPlacesReceived(places: List<PointOfInterest>)
        fun onPlacesError(message: String)
        fun onGeoAnchorPlaced(placeId: String, placeName: String)
        fun onGeospatialNotSupported()
        fun showSnackbar(message: String)
        fun navigateToPlaceDetails(placeId: String) // Add navigation callback
    }

    private var callback: GeoAnchorCallback? = null

    fun setCallback(callback: GeoAnchorCallback) {
        this.callback = callback
    }

    private val handler = Handler(Looper.getMainLooper())
    private val locationDistanceChecker = object : Runnable {
        override fun run() {
            networkScope.launch {
                val currentLocation = getLocationOptimized()
                val lastLocation = lastFetchedLocation

                if (currentLocation != null && lastLocation != null) {
                    val distance = currentLocation.distanceTo(lastLocation)
                    Log.d(TAG, "Distance since last fetch: $distance meters")

                    if (distance >= DISTANCE_THRESHOLD_METERS) {
                        Log.d(TAG, "Distance threshold passed. Fetching new places.")
                        getNearbyPlacesForAR()
                        lastFetchedLocation = currentLocation
                    }
                } else if (currentLocation != null && lastLocation == null) {
                    lastFetchedLocation = currentLocation
                    getNearbyPlacesForAR()
                }
            }

            handler.postDelayed(this, DISTANCE_CHECK_INTERVAL)
        }
    }

    private val filterChangeListener: () -> Unit = {
        Log.d(TAG, "Filter list changed – fetching places again.")
        getNearbyPlacesForAR()
    }

    fun startDistanceMonitoring() {
        FilterListManager.addListener(filterChangeListener)
        handler.post(locationDistanceChecker)
    }

    fun stopDistanceMonitoring() {
        FilterListManager.removeListener(filterChangeListener)
        handler.removeCallbacks(locationDistanceChecker)
    }

    fun getNearbyPlacesForAR() {
        val categories = FilterListManager.getAllFilters()
        Log.d(TAG, "Fetching nearby places for AR...")
        Log.d(TAG, "Selected Filters: $categories")

        if (categories.isEmpty()) {
            Log.i(TAG, "No filters selected – clearing anchors and skipping request.")
            pendingPlaces = null
            shouldPlaceGeoAnchors = false
            arSceneRenderer.getLayerManager().clearLayerLabels()
            return
        }

        networkScope.launch {
            val currentLocation = getLocationOptimized()
            Log.d(TAG, "Location result: $currentLocation")

            if (currentLocation == null) {
                Log.e(TAG, "Location is null! Aborting fetch.")
                withContext(Dispatchers.Main) {
                    callback?.onPlacesError("Unable to get current location")
                }
                return@launch
            }

            geoLocationUtils.updateLocation(currentLocation)

            // Use the singleton repository instance
            val result = nearbyPlacesRepository.fetchNearbyPlaces(
                currentLocation.latitude,
                currentLocation.longitude,
                categories
            )

            Log.d(TAG, result.toString())

            withContext(Dispatchers.Main) {
                result.onSuccess { places ->
                    Log.d(TAG, "Received ${places.size} places")
                    callback?.onPlacesReceived(places)
                    lastFetchedLocation = currentLocation

                    //val mockPlaces = createMockPointsOfInterest()
                    val combinedPlaces = places.toMutableList().apply {  }
                    pendingPlaces = combinedPlaces
                    shouldPlaceGeoAnchors = true
                }

                result.onFailure { error ->
                    Log.e(TAG, "Error fetching places: ${error.localizedMessage}")
                    val errorMessage = "Couldn't load nearby places: ${error.message}"
                    callback?.onPlacesError(errorMessage)
                }
            }
        }
    }

//    private fun createMockPointsOfInterest(): List<PointOfInterest> {
//        // Using Hod Hasharon coordinates as a base for mock data
//        val baseLat = 32.142924
//        val baseLng = 34.887320
//
//        val mockPlaces = listOf(
//            PointOfInterest(
//                id = "mock_cafe_123",
//                name = "Mock Coffee Corner",
//                location = GeoLocation(lat = baseLat + 0.000008, lng = baseLng + 0.000008),
//                rating = 4.2F,
//                type = "cafe",
//                address = "1 Mock Road, Hod Hasharon",
//                phoneNumber = "050-1112233",
//                businessStatus = "OPERATIONAL",
//                openingHours = OpeningHours(true, listOf("Mon-Fri: 7 AM - 6 PM", "Sat: 9 AM - 2 PM")),
//                elevation = 15.0
//            ),
//            PointOfInterest(
//                id = "mock_museum_456",
//                name = "Virtual History Museum",
//                location = GeoLocation(lat = baseLat, lng = baseLng),
//                rating = 4.8F,
//                type = "museum",
//                address = "789 Pixel Lane, Hod Hasharon",
//                phoneNumber = "null",
//                businessStatus = "OPERATIONAL",
//                openingHours = OpeningHours(false, listOf("Mon-Sun: 10 AM - 5 PM (Closed on holidays)")),
//                elevation = 15.0
//            )
//        )
//        return mockPlaces
//    }

    fun handleGeoAnchorPlacement(session: Session): Boolean {
        val earth = session.earth
        if (shouldPlaceGeoAnchors && earth != null && earth.trackingState == TrackingState.TRACKING) {
            callback?.showSnackbar("Placing nearby locations...")

            pendingPlaces?.let { places ->
                updateARViewWithPlaces(places, session)
                shouldPlaceGeoAnchors = false
                pendingPlaces = null
                return true
            }
        }
        return false
    }

    private fun updateARViewWithPlaces(places: List<PointOfInterest>, session: Session) {
        Log.d(TAG, "updateARViewWithPlaces")

        val earth = session.earth ?: return

        if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
            Handler(Looper.getMainLooper()).post {
                callback?.onGeospatialNotSupported()
            }
            return
        }

        if (earth.trackingState != TrackingState.TRACKING) {
            Log.d(TAG, "earth.trackingState != TrackingState.TRACKING")
            return
        }

        val layerManager = arSceneRenderer.getLayerManager()
        val existingPlaceIds = layerManager.getExistingPlaceIds()

        for (point in places) {
            if (existingPlaceIds.contains(point.id)) continue

            val cameraPose = earth.cameraGeospatialPose
            val targetAltitude = cameraPose.altitude - 0.5
            val targetLat = point.location.lat
            val targetLng = point.location.lng

            val headingToPoint = computeBearing(
                cameraPose.latitude,
                cameraPose.longitude,
                targetLat,
                targetLng
            )
            val correctedHeading = (headingToPoint + 180) % 360
            val headingQuaternion = calculateHeadingQuaternion(correctedHeading)

            val anchor = earth.createAnchor(targetLat, targetLng, targetAltitude, headingQuaternion)
            Log.d(
                TAG,
                "Created Anchor at $targetLat, $targetLng, $targetAltitude for ${point.name}"
            )

            val placeMap = createPlaceMap(point)
            layerManager.addLayerLabel(anchor, placeMap)

            callback?.onGeoAnchorPlaced(point.id, point.name)

        }
        places.firstOrNull()?.let { firstPlace ->
            showLayerDetailAsFullScreen(firstPlace.id)
        }
    }

    /**
     * Call this when user taps on an AR anchor to navigate to place details
     */
    fun onAnchorTapped(placeId: String) {
        Log.d(TAG, "Anchor tapped for place: $placeId")
        //callback?.navigateToPlaceDetails(placeId)
        showLayerDetailAsFullScreen(placeId)
    }

    private fun showLayerDetailAsFullScreen(placeId: String) {
        try {
            activity.runOnUiThread {
                val fragment = LayerDetailFragment.newInstance(placeId)

                // Hide the AR view
                view.root.visibility = android.view.View.GONE

                // Add fragment directly to the main content container
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment, "LayerDetailFragment")
                    .addToBackStack("LayerDetail")
                    .commitAllowingStateLoss()

                Log.d(TAG, "Added LayerDetailFragment directly to content for placeId: $placeId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing LayerDetailFragment", e)
            callback?.showSnackbar("Error opening place details")
        }
    }

    private fun createPlaceMap(point: PointOfInterest): Map<String, Any?> {
        return mapOf(
            "place_id" to point.id,
            "name" to point.name,
            "location" to mapOf(
                "lat" to point.location.lat,
                "lng" to point.location.lng
            ),
            "rating" to point.rating,
            "type" to point.type,
            "address" to point.address,
            "phone_number" to point.phoneNumber,
            "business_status" to point.businessStatus,
            "opening_hours" to point.openingHours?.let {
                mapOf(
                    "open_now" to it.openNow,
                    "weekday_text" to it.weekdayText
                )
            }
        )
    }

    private suspend fun getLocationOptimized(): Location? {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLocationUpdate < LOCATION_UPDATE_INTERVAL) {
            return locationCache["current"]
        }

        return try {
            val location = geoLocationUtils.getSingleCurrentLocation()
            location?.let {
                locationCache["current"] = it
                lastLocationUpdate = currentTime
            }
            location
        } catch (e: Exception) {
            Log.e(TAG, "Location update failed", e)
            locationCache.remove("current")
            null
        }
    }

    private fun computeBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    private fun calculateHeadingQuaternion(headingDegrees: Double): FloatArray {
        val headingRadians = Math.toRadians(headingDegrees)
        val halfAngle = headingRadians / 2.0

        return floatArrayOf(
            0f, // x
            (sin(halfAngle)).toFloat(), // y (rotation around Y-axis)
            0f, // z
            (cos(halfAngle)).toFloat()  // w
        )
    }

    fun clearLocationCache() {
        locationCache.clear()
        lastLocationUpdate = 0L
    }

    fun getCachedLocation(): Location? {
        return locationCache["current"]
    }
}