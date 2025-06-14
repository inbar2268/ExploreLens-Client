package com.example.explorelens.ar
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.ArActivity
import com.example.explorelens.model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.google.ar.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.ar.components.ARSceneRenderer
import com.example.explorelens.ar.components.ARTouchInteractionManager
import com.example.explorelens.ar.components.AnchorManager
import com.example.explorelens.ar.components.GeoAnchorManager
import com.example.explorelens.ar.components.SiteHistoryHelper
import com.example.explorelens.ar.components.SnapshotManager
import com.example.explorelens.ar.render.FilterListManager
import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import com.example.explorelens.model.ARLabeledAnchor
import com.example.explorelens.utils.GeoLocationUtils
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren


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

    lateinit var view: ArActivityView
    val displayRotationHelper = DisplayRotationHelper(activity)

    var serverResult: ImageAnalyzedResult? = null
    private var lastSnapshotData: Snapshot? = null

    private lateinit var anchorManager: AnchorManager
    private lateinit var arTouchInteractionManager: ARTouchInteractionManager
    private lateinit var snapshotManager: SnapshotManager
    private lateinit var arSceneRenderer: ARSceneRenderer
    private lateinit var geoAnchorManager: GeoAnchorManager
    private lateinit var siteHistoryHelper: SiteHistoryHelper

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        if (FilterListManager.getAllFilters().isNotEmpty())
            geoAnchorManager.startDistanceMonitoring()
        Handler(Looper.getMainLooper()).postDelayed({

        }, 1000)
        Log.e(TAG, "anchors amount: ${anchorManager.arLabeledAnchors.size}")
    }

    override fun onPause(owner: LifecycleOwner) {
        networkScope.coroutineContext.cancelChildren()
        backgroundScope.coroutineContext.cancelChildren()
        geoAnchorManager.stopDistanceMonitoring()
        displayRotationHelper.onPause()
        super.onPause(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        networkScope.cancel()
        backgroundScope.cancel()
        anchorManager.clear()
        geoAnchorManager.clearLocationCache()
        super.onDestroy(owner)
    }

    fun bindView(view: ArActivityView) {
        this.view = view
        arSceneRenderer = ARSceneRenderer(activity, displayRotationHelper)
        anchorManager = AnchorManager(activity, view, networkScope)
        arTouchInteractionManager = ARTouchInteractionManager(activity, view, anchorManager)
        siteHistoryHelper = SiteHistoryHelper(siteHistoryViewModel, geoLocationUtils, networkScope)
        snapshotManager = SnapshotManager(
            activity.applicationContext,
            view,
            displayRotationHelper,
            networkScope,
            backgroundScope
        )
        geoAnchorManager = GeoAnchorManager(
            activity,
            view,
            geoLocationUtils,
            arSceneRenderer,
            networkScope
        )
        setupCallbacks()
    }

    private fun setupCallbacks() {
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

            override fun onLayerLabelClicked(layerLabel: ARLayerManager.LayerLabelInfo) {
                val placeName = layerLabel.placeInfo["name"] as? String ?: "Unknown Place"
                Log.d(TAG, "Layer label clicked: $placeName")
            }

            override fun onLayerLabelClosed(layerLabel: ARLayerManager.LayerLabelInfo) {
            }
        })

        geoAnchorManager.setCallback(object : GeoAnchorManager.GeoAnchorCallback {
            override fun onPlacesReceived(places: List<PointOfInterest>) {
                Log.d(TAG, "Received ${places.size} places from GeoAnchorManager")
            }

            override fun onPlacesError(message: String) {
                showSnackbar(message)
            }

            override fun onGeoAnchorPlaced(placeId: String, placeName: String) {
                Log.d(TAG, "Geo anchor placed: $placeName")
            }

            override fun onGeospatialNotSupported() {
                showSnackbar("Geospatial API not supported")
            }

            override fun showSnackbar(message: String) {
                this@AppRenderer.showSnackbar(message)
            }
        })

        arSceneRenderer.setTouchHandler(arTouchInteractionManager)
        arTouchInteractionManager.setLayerManager(arSceneRenderer.getLayerManager())
        arTouchInteractionManager.setupCameraButton()
    }


    override fun onSurfaceCreated(render: SampleRender) {
        arSceneRenderer.onSurfaceCreated(render)
    }

    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        arSceneRenderer.onSurfaceChanged(render, width, height)
    }


    override fun onDrawFrame(render: SampleRender) {
        val session = activity.arCoreSessionHelper.sessionCache ?: return

        // Update AR session
        val frame = try {
            session.update()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating AR session", e)
            showSnackbar("AR session error. Try restarting the app.")
            return
        }

        // Handle scan button press
        handleScanButtonPress(frame, session)

        // Process pending touch interactions
        arTouchInteractionManager.processPendingTouch(frame, session)

        // Process object detection results
        processObjectResults(frame, session)

        // Handle geo-spatial anchor placement
        geoAnchorManager.handleGeoAnchorPlacement(session)

        // Delegate rendering to ARSceneRenderer
        val renderResult = arSceneRenderer.drawFrame(
            render,
            frame,
            anchorManager.getAnchorsForRendering(),
            lastSnapshotData
        )

        // Handle render results
        handleRenderResult(renderResult)
    }

    private fun handleRenderResult(result: ARSceneRenderer.RenderResult) {
        when (result) {
            is ARSceneRenderer.RenderResult.CameraError -> {
                showSnackbar("Camera error: ${result.message}")
            }
            is ARSceneRenderer.RenderResult.UnknownError -> {
                Log.e(TAG, "Rendering error: ${result.message}")
            }
            else -> {
                // Success or other handled states
            }
        }
    }

    private fun handleScanButtonPress(frame: Frame, session: Session) {
        if (arTouchInteractionManager.scanButtonWasPressed) {
            val camera = frame.camera
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
    }

    private fun processObjectResults(frame: Frame, session: Session) {
        val obj = serverResult
        if (obj != null) {
            serverResult = null
            val snapshotData = lastSnapshotData
            Log.i(TAG, "Analyzer got objects: $obj")

            if (snapshotData == null) {
                Log.e(TAG, "No snapshot data available for anchor creation")
                updateDetectionUI(null)
                return
            }

            val anchor = anchorManager.createAnchorFromAnalyzedResult(session, snapshotData, obj, frame)

            if (anchor != null) {
                anchorManager.addAnchor(anchor)
            }

            saveDetectionHistory(obj, anchor)
            updateDetectionUI(anchor)
        }
    }

    private fun updateDetectionUI(anchor: ARLabeledAnchor?) {
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

    private fun saveDetectionHistory(result: ImageAnalyzedResult, anchor: ARLabeledAnchor?) {
        launch {
            val currentLocation = geoAnchorManager.getCachedLocation()
                ?: geoLocationUtils.getSingleCurrentLocation()

            siteHistoryHelper.createSiteHistoryForDetectedObject(result, currentLocation)
        }
    }

    private fun showSnackbar(message: String): Unit =
        view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() =
        activity.view.snackbarHelper.hide(activity)

    fun handleTouch(x: Float, y: Float) {
        arTouchInteractionManager.handleTouch(x, y)
    }

    fun getNearbyPlacesForAR() {
        geoAnchorManager.startDistanceMonitoring()
    }

    private fun createSiteHistoryForDetectedObject(
        result: ImageAnalyzedResult,
        location: Location?
    ) {
        networkScope.launch { // Or
            val currentLocation = location ?: geoLocationUtils.getSingleCurrentLocation()
            currentLocation?.let {
                geoLocationUtils.updateLocation(it)
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
}