package com.example.explorelens.ar

import android.location.Location
import android.media.Image
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.explorelens.ArActivity
import com.example.explorelens.extensions.convertYuv
import com.example.explorelens.extensions.toFile
import com.example.explorelens.model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.arcore.BackgroundRenderer
import com.example.explorelens.ar.classification.utils.ImageUtils
import com.example.explorelens.ar.render.LabelRender
import com.example.explorelens.ar.render.PointCloudRender
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.example.explorelens.data.model.siteDetectionData.SiteInformation
import kotlinx.coroutines.withContext
import java.lang.Thread.sleep
import java.util.Collections
import kotlin.math.sqrt
import com.example.explorelens.BuildConfig
import com.example.explorelens.R
import com.example.explorelens.Model
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.ar.ArActivityView.Companion
import com.example.explorelens.ar.render.LayerLabelRenderer
import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.model.ARLabeledAnchor
import com.example.explorelens.utils.GeoLocationUtils
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.detectionResult.AnalyzedResultApi
import com.example.explorelens.data.repository.DetectionResultRepository
import com.example.explorelens.data.repository.NearbyPlacesRepository
import com.example.explorelens.data.repository.SiteDetailsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.ar.core.Config
import com.example.explorelens.ar.components.AnchorManager
import com.example.explorelens.ar.components.ARTouchInteractionManager
import com.example.explorelens.ar.components.SnapshotManager

class AppRenderer(
    val activity: ArActivity,
    private val geoLocationUtils: GeoLocationUtils,
    private val siteHistoryViewModel: SiteHistoryViewModel
) : DefaultLifecycleObserver, SampleRender.Renderer,
    CoroutineScope by MainScope() {
    companion object {
        val TAG = "HelloArRenderer"
    }

    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Caching for Location
    private val locationCache = mutableMapOf<String, Location>()
    private var lastLocationUpdate = 0L
    private val LOCATION_UPDATE_INTERVAL = 30_000L // 30 seconds

    lateinit var view: ArActivityView
    val displayRotationHelper = DisplayRotationHelper(activity)
    lateinit var backgroundRenderer: BackgroundRenderer
    val pointCloudRender = PointCloudRender()
    val labelRenderer = LabelRender()


    private val layerManager = ARLayerManager(activity)

    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val viewProjectionMatrix = FloatArray(16)

    var serverResult: ImageAnalyzedResult? = null
//    var scanButtonWasPressed = false

    private var lastSnapshotData: Snapshot? = null
//    var arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
//    private val reusableAnchorList = ArrayList<ARLabeledAnchor>()
//    private var hasLoadedAnchors = false
//    private val convertFloats = FloatArray(4)
//    private val convertFloatsOut = FloatArray(4)
    private var shouldPlaceGeoAnchors = false
    private var pendingPlaces: List<PointOfInterest>? = null

    private lateinit var anchorManager: AnchorManager
    private lateinit var arTouchInteractionManager: ARTouchInteractionManager
    private lateinit var snapshotManager: SnapshotManager

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        Handler(Looper.getMainLooper()).postDelayed({

        }, 1000)
        Log.e(TAG, "anchors amount: ${anchorManager.arLabeledAnchors.size}")
    }

    override fun onPause(owner: LifecycleOwner) {
        networkScope.coroutineContext.cancelChildren()
        backgroundScope.coroutineContext.cancelChildren()
        displayRotationHelper.onPause()
        super.onPause(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        networkScope.cancel()
        backgroundScope.cancel()
        locationCache.clear()
        anchorManager.clear()
        super.onDestroy(owner)
    }

    fun bindView(view: ArActivityView) {
        this.view = view
        anchorManager = AnchorManager(activity, view, networkScope)
        arTouchInteractionManager = ARTouchInteractionManager(activity, view, anchorManager)
        snapshotManager = SnapshotManager(
            activity.applicationContext,
            view,
            displayRotationHelper,
            networkScope,
            backgroundScope
        )

        snapshotManager.setCallback(object : SnapshotManager.SnapshotCallback {
            override fun onSnapshotResult(result: ImageAnalyzedResult?) {
                serverResult = result
            }

            override fun onSnapshotError(message: String) {
                showSnackbar(message)
            }

            override fun showSnackbar(message: String) {
                this@AppRenderer.showSnackbar(message)
            }
        })

        arTouchInteractionManager.setTouchInteractionListener(object : ARTouchInteractionManager.TouchInteractionListener {
            override fun onScanButtonPressed() {
            }

            override fun onAnchorClicked(anchor: ARLabeledAnchor) {
            }
        })
        arTouchInteractionManager.setupCameraButton()
    }


    override fun onSurfaceCreated(render: SampleRender) {
        backgroundRenderer = BackgroundRenderer(render).apply {
            setUseDepthVisualization(render, false)
        }
        pointCloudRender.onSurfaceCreated(render)
        labelRenderer.onSurfaceCreated(render, activity)
        layerManager.onSurfaceCreated(render)
    }

    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        val session = activity.arCoreSessionHelper.sessionCache ?: return
        val earth = session.earth
        session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
        displayRotationHelper.updateSessionIfNeeded(session)
        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during onDrawFrame", e)
            showSnackbar("Camera not available. Try restarting the app.")
            return
        }

        backgroundRenderer.updateDisplayGeometry(frame)
        backgroundRenderer.drawBackground(render)

        val camera = frame.camera
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

            if (arTouchInteractionManager.scanButtonWasPressed) {
            if (camera.trackingState != TrackingState.TRACKING) {
                view.post {
                    view.setScanningActive(false)
                    showSnackbar("Please wait for AR to initialize. Move your device around to scan the environment.")
                }
            } else {
                lastSnapshotData = snapshotManager.takeSnapshot(frame, session)
                anchorManager.initialize()
            }
            arTouchInteractionManager.resetScanButton()
        }

        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera is not tracking.")
            return
        }
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }

        arTouchInteractionManager.processPendingTouch(frame, session)

        processObjectResults(frame, session)
        drawAnchors(render, frame)


        if (shouldPlaceGeoAnchors && earth != null && earth.trackingState == TrackingState.TRACKING) {
            showSnackbar("updateARViewWithPlaces")
            pendingPlaces?.let {
                updateARViewWithPlaces(it)
                shouldPlaceGeoAnchors = false
                pendingPlaces = null
            }
        }
        layerManager.drawLayerLabels(render, viewProjectionMatrix, camera.pose, frame)

    }


    private fun drawAnchors(render: SampleRender, frame: Frame) {

        val anchorsToDraw = anchorManager.getAnchorsForRendering()
        for (arDetectedObject in anchorsToDraw) {
            val anchor = arDetectedObject.anchor
            if (anchor.trackingState != TrackingState.TRACKING) {
                continue
            }
            Log.d(TAG, "Anchor tracking state: ${anchor.trackingState}")
            Log.d(TAG, "Label: ${arDetectedObject.label}")
            if (anchor.trackingState != TrackingState.TRACKING) continue
            labelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchor.pose,
                lastSnapshotData?.cameraPose ?: frame.camera.pose,
                arDetectedObject.label
            )
        }
    }

    private fun processObjectResults(frame: Frame, session: Session) {
        val obj = serverResult
        if (obj != null) {
            serverResult = null
            val snapshotData = lastSnapshotData
            Log.i(TAG, "Analyzer got objects: $obj")
            if (snapshotData == null) {
                Log.e(TAG, "No snapshot data available for anchor creation")
                view.post {
                    view.setScanningActive(false)
                    showSnackbar("Unable to place AR labels. Please try again.")
                }
                return
            }
            val anchor = anchorManager.createAnchorFromAnalyzedResult(session, snapshotData, obj, frame)

            launch {
                val currentLocation =
                    geoLocationUtils.getSingleCurrentLocation() // Await the result
                if (anchor != null) {
                    anchorManager.addAnchor(anchor)
                }
                createSiteHistoryForDetectedObject(obj, currentLocation) // Pass the location
            }

            if (anchor != null) {
                anchorManager.addAnchor(anchor)
            }

            view.post {
                view.setScanningActive(false)

                when {
                    anchor == null ->
                        showSnackbar("No landmark detected. Try scanning again.")

                    anchor.anchor.trackingState != TrackingState.TRACKING ->
                        showSnackbar("Could not anchor the landmark. Try moving your device around.")

                    else ->
                        showSnackbar("Tap on the label to see more details")
                }
            }
        }
    }


    private fun showSnackbar(message: String): Unit =
        view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() =
        activity.view.snackbarHelper.hide(activity)

//    private var pendingTouchX: Float? = null
//    private var pendingTouchY: Float? = null

    fun handleTouch(x: Float, y: Float) {
        arTouchInteractionManager.handleTouch(x, y)
    }

    fun getNearbyPlacesForAR(categories: List<String>) {
        Log.d("NearbyPlaces", "Fetching nearby places for AR...")
        Log.d("NearbyPlaces", "Selected Filters (on Apply): $categories")

        networkScope.launch{
            Log.d("NearbyPlaces", "Fetching inside networckScope..")


            val currentLocation = getLocationOptimized()
            Log.d("NearbyPlaces", "Location result: $currentLocation")
            if (currentLocation == null) {
                Log.e("NearbyPlaces", "Location is null! Aborting fetch.")
                return@launch
            }
            geoLocationUtils.updateLocation(currentLocation)
            val repository = NearbyPlacesRepository()
            val result = repository.fetchNearbyPlaces(
                currentLocation.latitude,
                currentLocation.longitude,
                categories
            )

            Log.d("NearbyPlaces", result.toString())

            withContext(Dispatchers.Main) {
                result.onSuccess { places ->
                    Log.d("NearbyPlaces", "Received ${places} places")
                    showSnackbar("Received ${places.size} places")
                    pendingPlaces = places
                    shouldPlaceGeoAnchors = true

                }

                result.onFailure { error ->
                    Log.e("NearbyPlaces", "Error fetching places: ${error.localizedMessage}")
                    showSnackbar("Couldn't load nearby places: ${error.message}")
                }
            }
        }
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

    private fun updateARViewWithPlaces(places: List<PointOfInterest>) {
        Log.d("GeoAR", "updateARViewWithPlaces")

        val session = activity.arCoreSessionHelper.sessionCache ?: return
        val earth = session.earth ?: return

        if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
           Handler(Looper.getMainLooper()).post {
                showSnackbar("Geospatial API not supported")
            }
            return
        }

        if (earth.trackingState != TrackingState.TRACKING) {
            Log.d("GeoAR", "earth.trackingState != TrackingState.TRACKING")
            return
        }

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
                "GeoAR",
                "Created Anchor at $targetLat, $targetLng, $targetAltitude for ${point.name}"
            )

            val placeMap = mapOf(
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

            layerManager.addLayerLabel(anchor, placeMap)
        }
    }

    private fun createSiteHistoryForDetectedObject(
        result: ImageAnalyzedResult,
        location: Location?
    ) {
        networkScope.launch { // Or
            val currentLocation = getLocationOptimized() ?: return@launch
            geoLocationUtils.updateLocation(currentLocation)
            val geoHash = geoLocationUtils.getGeoHash() ?: ""
            siteHistoryViewModel.createSiteHistory(
                siteInfoId = result.siteInfoId,
                currentLocation
            )
            Log.d(
                TAG,
                "Saved site history with geoHash: $geoHash, lat: ${currentLocation.latitude}, long: ${currentLocation.longitude}"
            )
        }
    }

}
