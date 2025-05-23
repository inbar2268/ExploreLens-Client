package com.example.explorelens.ar

import android.location.Location
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
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.model.ARLabeledAnchor
import com.example.explorelens.utils.GeoLocationUtils
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.detectionResult.AnalyzedResultApi
import com.example.explorelens.data.repository.DetectionResultRepository
import com.example.explorelens.data.repository.NearbyPlacesRepository
import com.example.explorelens.data.repository.SiteDetailsRepository

class AppRenderer(
    val activity: ArActivity,
    private val geoLocationUtils: GeoLocationUtils,
    private val siteHistoryViewModel: SiteHistoryViewModel
) : DefaultLifecycleObserver, SampleRender.Renderer,
    CoroutineScope by MainScope() {
    companion object {
        val TAG = "HelloArRenderer"
    }

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
    var scanButtonWasPressed = false

    private var lastSnapshotData: Snapshot? = null
    var arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
    private var hasLoadedAnchors = false
    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    private var mockLayerAnchor: ARLayerManager.LayerLabelInfo? = null
    private val mockPlaceData = mapOf(
        "place_id" to "mock-place-2",
        "name" to "Gym Mock Place",
        "location" to mapOf(
            "lat" to 32.42773353018067,
            "lng" to 34.933969622735574
        ),
        "rating" to 4.5,
        "type" to "gym",
        "address" to "123 Mock Gym St.",
        "phone_number" to "000-000-0000",
        "business_status" to "OPERATIONAL",
        "opening_hours" to mapOf(
            "open_now" to true,
            "weekday_text" to listOf(
                "Monday: 9:00 AM – 5:00 PM",
                "Tuesday: 9:00 AM – 5:00 PM",
                "Wednesday: 9:00 AM – 5:00 PM",
                "Thursday: 9:00 AM – 5:00 PM",
                "Friday: 9:00 AM – 5:00 PM",
                "Saturday: 10:00 AM – 4:00 PM",
                "Sunday: Closed"
            )
        )
    )

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        Handler(Looper.getMainLooper()).postDelayed({

        }, 1000)
        Log.e(TAG, "anchors amount: ${arLabeledAnchors.size}")
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    fun bindView(view: ArActivityView) {
        this.view = view


//        view.snapshotButton.setOnClickListener {
//            scanButtonWasPressed = true
//            view.setScanningActive(true)
//            hideSnackbar()
//        }

        view.binding.cameraButtonContainer.setOnTouchListener { _, event ->
            scanButtonWasPressed = true
            view.setScanningActive(true)
            hideSnackbar()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.binding.cameraInnerCircle.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.binding.cameraInnerCircle.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }
    }


    override fun onSurfaceCreated(render: SampleRender) {
        backgroundRenderer = BackgroundRenderer(render).apply {
            setUseDepthVisualization(render, false)
        }
        pointCloudRender.onSurfaceCreated(render)

        // Pass the activity context to labelRenderer
        labelRenderer.onSurfaceCreated(render, activity)

        layerManager.onSurfaceCreated(render)

        getNearbyPlacesForAR(listOf("bar", "hotel"))
    }
    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        val session = activity.arCoreSessionHelper.sessionCache ?: return
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

        if (scanButtonWasPressed) {
            if (camera.trackingState != TrackingState.TRACKING) {
                view.post {
                    view.setScanningActive(false)
                    showSnackbar("Please wait for AR to initialize. Move your device around to scan the environment.")
                }
            } else {
                lastSnapshotData = takeSnapshot(frame, session)
                if (!hasLoadedAnchors) {
                    getAllAnchors()
                    hasLoadedAnchors = true
                }
            }
            scanButtonWasPressed = false
        }
        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera is not tracking.")
            return
        }
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }
        pendingTouchX?.let { x ->
            pendingTouchY?.let { y ->
                processTouchInGLThread(x, y, frame, session)
                // Clear the pending touch
                pendingTouchX = null
                pendingTouchY = null
            }
        }
        processObjectResults(frame, session)
        drawAnchors(render, frame)
        layerManager.drawLayerLabels(render, viewProjectionMatrix, camera.pose, frame)
    }

    private fun drawAnchors(render: SampleRender, frame: Frame) {
        val anchorsToRender = ArrayList<ARLabeledAnchor>()
        synchronized(arLabeledAnchors) {
            anchorsToRender.addAll(arLabeledAnchors)
        }

        for (arDetectedObject in anchorsToRender) {
            val anchor = arDetectedObject.anchor

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
            val anchor = fetchAndCreateAnchor(session, snapshotData, obj, frame)
            mockLayerAnchor = createLayerLabelAnchor(session, snapshotData, obj, frame)

            launch {
                val currentLocation =
                    geoLocationUtils.getSingleCurrentLocation() // Await the result
                if (anchor != null) {
                    addAnchorToDatabase(anchor)
                }
                createSiteHistoryForDetectedObject(obj, currentLocation) // Pass the location
            }

            if (anchor != null) {
                addAnchorToDatabase(anchor)
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

    private fun placeLabelAccurateWithSnapshot(
        session: Session,
        snapshotPose: Pose,
        labelX: Float, // Normalized 0-1
        labelY: Float, // Normalized 0-1
        frame: Frame
    ): Anchor? {
        // Log input coordinates
        Log.d(TAG, "Input coordinates (normalized): x=$labelX, y=$labelY")
        Log.d(
            TAG,
            "Snapshot pose: tx=${snapshotPose.tx()}, ty=${snapshotPose.ty()}, tz=${snapshotPose.tz()}"
        )

        // 1. Calculate the direction vector in camera space
        // Convert normalized coordinates (0-1) to view space (-1 to 1)
        val viewportX = (labelX * 2.0f) - 1.0f
        val viewportY = -((labelY * 2.0f) - 1.0f) // Negate because Y is flipped

        // Direction vector in camera space (z is negative because camera looks down negative z-axis)
        val directionVector = floatArrayOf(viewportX, viewportY, -1.0f)

        // 2. Transform direction vector to world space using snapshot pose
        val worldDirection = snapshotPose.transformPoint(directionVector)

        // 3. Normalize the direction vector
        val magnitude = sqrt(
            worldDirection[0] * worldDirection[0] +
                    worldDirection[1] * worldDirection[1] +
                    worldDirection[2] * worldDirection[2]
        )

        val normalizedDirection = floatArrayOf(
            worldDirection[0] / magnitude,
            worldDirection[1] / magnitude,
            worldDirection[2] / magnitude
        )

        // 4. Convert the input coordinates to view coordinates for the hit test
        // Convert normalized coordinates to image pixel coordinates
        val imageWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val imageHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        val pixelX = labelX * imageWidth
        val pixelY = labelY * imageHeight

        // Convert to view coordinates
        convertFloats[0] = pixelX
        convertFloats[1] = pixelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // 5. Perform a hit test at the actual input coordinates (not center)
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])

        // Default distance if no hit is found
        var distance = 2.0f

        if (hits.isNotEmpty()) {
            val hitResult = hits.first()
            distance = hitResult.distance
            Log.d(TAG, "Hit test succeeded: distance=$distance")
        } else {
            Log.d(TAG, "Hit test failed, using default distance: $distance")
        }

        // 6. Calculate final world position
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + normalizedDirection[0] * distance,
            snapshotPose.ty() + normalizedDirection[1] * distance, // No Y correction needed
            snapshotPose.tz() + normalizedDirection[2] * distance
        )

        // 7. Create anchor with final position
        val anchorPose = Pose.makeTranslation(
            worldPosition[0],
            worldPosition[1],
            worldPosition[2]
        )

        Log.d(
            TAG,
            "Created anchor at: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}"
        )

        return session.createAnchor(anchorPose)
    }

    private fun placeLabelRelativeToSnapshot(
        session: Session,
        snapshotPose: Pose,
        labelX: Float, // Normalized 0-1
        labelY: Float, // Normalized 0-1
        frame: Frame
    ): Anchor? {
        // Log input coordinates
        Log.d(TAG, "Input coordinates (normalized): x=$labelX, y=$labelY")
        Log.d(
            TAG,
            "Snapshot pose: tx=${snapshotPose.tx()}, ty=${snapshotPose.ty()}, tz=${snapshotPose.tz()}"
        )

        // 1. Calculate the direction vector in camera space
        // Convert normalized coordinates (0-1) to view space (-1 to 1)
        val viewportX = (labelX * 2.0f) - 1.0f
        val viewportY = -((labelY * 2.0f) - 1.0f) // Negate because Y is flipped

        // Direction vector in camera space (z is negative because camera looks down negative z-axis)
        val directionVector = floatArrayOf(viewportX, viewportY, -1.0f)

        // 2. Transform direction vector to world space using snapshot pose
        // Use Pose.transformPoint() which is available in ARCore
        val worldDirection = snapshotPose.transformPoint(directionVector)

        // 3. Normalize the direction vector
        val magnitude = sqrt(
            worldDirection[0] * worldDirection[0] +
                    worldDirection[1] * worldDirection[1] +
                    worldDirection[2] * worldDirection[2]
        )

        val normalizedDirection = floatArrayOf(
            worldDirection[0] / magnitude,
            worldDirection[1] / magnitude,
            worldDirection[2] / magnitude
        )

        // 4. Perform a hit test using the current frame to find a suitable depth
        val centerX = frame.camera.imageIntrinsics.imageDimensions[0] / 2f
        val centerY = frame.camera.imageIntrinsics.imageDimensions[1] / 2f

        // Convert to view coordinates for hit test
        convertFloats[0] = centerX
        convertFloats[1] = centerY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])

        // Default distance if no hit is found
        var distance = 2.0f

        if (hits.isNotEmpty()) {
            val hitResult = hits.first()
            distance = hitResult.distance
            Log.d(TAG, "Hit test succeeded: distance=$distance")
        } else {
            Log.d(TAG, "Hit test failed, using default distance: $distance")
        }

        // 5. Calculate final world position
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + normalizedDirection[0] * distance,
            snapshotPose.ty() + normalizedDirection[1] * distance, // Lower by 20cm
            snapshotPose.tz() + normalizedDirection[2] * distance
        )

        // 6. Create anchor with final position
        val anchorPose = Pose.makeTranslation(
            worldPosition[0],
            worldPosition[1],
            worldPosition[2]
        )

        Log.d(
            TAG,
            "Created anchor at: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}"
        )

        return session.createAnchor(anchorPose)
    }

    private fun placeLabelNotRelativeToSnapshot(
        session: Session,
        snapshotPose: Pose,
        labelX: Float,
        labelY: Float,
        frame: Frame
    ): Anchor? {
        // Convert normalized coordinates (0.5, 0.5) to image pixel coordinates
        val imageWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val imageHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        // If server is sending  (0-1), convert to pixels
        val pixelX = if (labelX <= 1.0f) labelX * imageWidth else labelX
        val pixelY = if (labelY <= 1.0f) labelY * imageHeight else labelY

        // Now convert to view coordinates
        convertFloats[0] = pixelX
        convertFloats[1] = pixelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Hit test at the converted view coordinates
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull() ?: return null

        // Get the hit pose and distance
        val hitPose = hitResult.hitPose
        val hitDistance = hitResult.distance

        // Use the hit pose directly but with a Y-adjustment
        val worldPosition = floatArrayOf(
            hitPose.tx(),
            hitPose.ty(),
            hitPose.tz()
        )

        // Create the anchor with the adjusted position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])

        Log.d(
            TAG,
            "Created anchor at x=${anchorPose.tx()}, y=${anchorPose.ty()}, z=${anchorPose.tz()} from normalized (${labelX}, ${labelY})"
        )

        return session.createAnchor(anchorPose)
    }

    private fun takeSnapshot(frame: Frame, session: Session): Snapshot {
        val camera = frame.camera
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
        val context = activity.applicationContext
        var path: String? = null;
        try {
            frame.tryAcquireCameraImage()?.use { cameraImage ->
                val cameraId = session.cameraConfig.cameraId
                val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
                val convertYuv = convertYuv(context, cameraImage)
                val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)

                val file = rotatedImage.toFile(context, "snapshot")
                path = file.absolutePath

            }
        } catch (e: NotYetAvailableException) {
            Log.e("takeSnapshot", "No image available yet")
        }

        if (BuildConfig.USE_MOCK_DATA) {
            path?.let {
                Log.d("Snapshot", "Calling getAnalyzedResult with path: $it")
            }
            val mockResults =
                ImageAnalyzedResult(
                    status = "assume",
                    description = "Famous site detected in full image.",
                    siteInformation = SiteInformation(
                        label = "full-image",
                        x = 0.5f,
                        y = 0.5f,
                        siteName = "Colosseum"
                    ),
                    siteInfoId = "68265e0d600fc819f657deb7",
                )

            launch(Dispatchers.Main) {
                sleep(2000)
                serverResult = mockResults
                view.setScanningActive(false)
            }
        } else {
            path?.let {
                Log.d("Snapshot", "Calling getAnalyzedResult with path: $it")
                getAnalyzedResult(it)
            }
            launch(Dispatchers.Main) {
                view.setScanningActive(false)
            }
        }


        val snapshot = Snapshot(
            timestamp = frame.timestamp,
            cameraPose = camera.pose,
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix
        )
        return snapshot
    }

    fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }


    private fun showSnackbar(message: String): Unit =
        view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

    private var pendingTouchX: Float? = null
    private var pendingTouchY: Float? = null

    fun handleTouch(x: Float, y: Float) {
        // Just store the coordinates for later processing
        Log.d(TAG, "Touch received at x=$x, y=$y")
        pendingTouchX = x
        pendingTouchY = y
    }

    private fun distanceBetween(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun processTouchInGLThread(x: Float, y: Float, frame: Frame, session: Session) {
        val camera = frame.camera

        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera is not tracking.")
            return
        }

        try {
            // Get camera position and forward direction
            val cameraPose = camera.pose
            val cameraPos = cameraPose.translation
            val forward = cameraPose.zAxis

            // Normalize forward direction (pointing out of the camera)
            val forwardNorm = floatArrayOf(-forward[0], -forward[1], -forward[2])

            // Find all visible anchors within a generous detection cone
            var closestAnchor: ARLabeledAnchor? = null
            var closestDistance = Float.MAX_VALUE

            // Log camera and touch information
            Log.d(TAG, "Camera position: ${cameraPos.contentToString()}")
            Log.d(TAG, "Touch coordinates: $x, $y")

            // Create a hit ray from the touch coordinates
            val hitResults = frame.hitTest(x, y)

            // Calculate ray direction from touch
            val ray = if (hitResults.isNotEmpty()) {
                val hitPos = hitResults.first().hitPose.translation
                val rayX = hitPos[0] - cameraPos[0]
                val rayY = hitPos[1] - cameraPos[1]
                val rayZ = hitPos[2] - cameraPos[2]

                val rayLength = sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ)
                if (rayLength > 0) {
                    floatArrayOf(rayX / rayLength, rayY / rayLength, rayZ / rayLength)
                } else {
                    forwardNorm
                }
            } else {
                // Fallback to camera forward direction if no hit
                forwardNorm
            }

            // Log ray direction
            Log.d(TAG, "Ray direction: ${ray.contentToString()}")

            val anchorsToCheck = ArrayList<ARLabeledAnchor>()
            synchronized(arLabeledAnchors) {
                anchorsToCheck.addAll(arLabeledAnchors)
            }

            // Examine each anchor to find the closest one to our touch ray
            for (anchor in anchorsToCheck) {
                val anchorPose = anchor.anchor.pose
                val anchorPos = anchorPose.translation  // FIXED: use anchorPose not anchorPos

                // Vector from camera to anchor
                val toAnchorX = anchorPos[0] - cameraPos[0]
                val toAnchorY = anchorPos[1] - cameraPos[1]
                val toAnchorZ = anchorPos[2] - cameraPos[2]

                val distanceToAnchor =
                    sqrt(toAnchorX * toAnchorX + toAnchorY * toAnchorY + toAnchorZ * toAnchorZ)

                // Calculate dot product to determine if anchor is in front of camera
                val dotProduct = toAnchorX * ray[0] + toAnchorY * ray[1] + toAnchorZ * ray[2]

                // Distance from ray to anchor (using vector rejection formula)
                val projection = dotProduct / distanceToAnchor
                val projectionDistance = distanceToAnchor * projection

                // Calculate the closest point on the ray
                val closestPointX = cameraPos[0] + ray[0] * projectionDistance
                val closestPointY = cameraPos[1] + ray[1] * projectionDistance
                val closestPointZ = cameraPos[2] + ray[2] * projectionDistance

                // Distance from this point to the anchor
                val dX = closestPointX - anchorPos[0]
                val dY = closestPointY - anchorPos[1]
                val dZ = closestPointZ - anchorPos[2]
                val perpendicularDistance = sqrt(dX * dX + dY * dY + dZ * dZ)

                val touchThreshold = 0.2f

                if (dotProduct > 0 && perpendicularDistance < touchThreshold) {
                    if (distanceToAnchor < closestDistance) {
                        closestAnchor = anchor
                        closestDistance = distanceToAnchor
                    }
                }

                Log.d(
                    TAG, "Anchor ${anchor.label}: distance=${distanceToAnchor}, " +
                            "perpendicular=${perpendicularDistance}, dot=${dotProduct}"
                )
            }

            // If we found an anchor, handle the click
            if (closestAnchor != null) {
                Log.d(TAG, "Selected anchor: ${closestAnchor.label} at distance ${closestDistance}")
                handleAnchorClick(closestAnchor)
            } else {
                Log.d(TAG, "No anchor selected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing touch", e)
        }
    }

    private fun fetchAndCreateAnchor(
        session: Session,
        snapshotData: Snapshot,
        obj: ImageAnalyzedResult,
        frame: Frame
    ): ARLabeledAnchor? {
        val siteInfo = obj.siteInformation
        val siteId = obj.siteInfoId

        if (!isValidSiteData(siteInfo, siteId)) return null

        val anchor = createAnchor(session, snapshotData.cameraPose, siteInfo!!.x, siteInfo.y, frame)
            ?: return null

        val initialLabel = "${siteInfo.siteName}||Loading..."
        val arLabeledAnchor = ARLabeledAnchor(anchor, initialLabel, siteInfo.siteName).apply {
            this.siteId = siteId
        }

        fetchAndUpdateSiteDetails(arLabeledAnchor, siteInfo.siteName, siteId)

        return arLabeledAnchor
    }

    private fun createLayerLabelAnchor(
        session: Session,
        snapshotData: Snapshot,
        obj: ImageAnalyzedResult,
        frame: Frame
    ): ARLayerManager.LayerLabelInfo? {
        val siteInfo = obj.siteInformation
        val siteId = obj.siteInfoId

        if (!isValidSiteData(siteInfo, siteId)) return null

        // Get the camera pose
        val cameraPose = snapshotData.cameraPose

        // First create an anchor at the same position
        val anchor = createAnchor(session, cameraPose, siteInfo!!.x, siteInfo.y, frame)
            ?: return null

        // Get the anchor's position
        val anchorPos = anchor.pose.translation

        // Calculate the direction from camera to anchor
        val dirX = anchorPos[0] - cameraPose.tx()
        val dirY = anchorPos[1] - cameraPose.ty()
        val dirZ = anchorPos[2] - cameraPose.tz()

        // Normalize the direction vector
        val length = kotlin.math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        val normDirX = dirX / length
        val normDirY = dirY / length
        val normDirZ = dirZ / length

        // Calculate right vector (perpendicular to direction and world up)
        val rightX = -normDirZ
        val rightY = 0f  // Assuming Y is up in world space
        val rightZ = normDirX

        // Normalize the right vector
        val rightLength = kotlin.math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
        val normRightX = rightX / rightLength
        val normRightY = rightY / rightLength
        val normRightZ = rightZ / rightLength

        // Add an offset to the right (0.5 meters)
        val offsetDistance = 0.5f
        val offsetX = anchorPos[0] + normRightX * offsetDistance
        val offsetY = anchorPos[1] + 0.15f  // Slightly higher
        val offsetZ = anchorPos[2] + normRightZ * offsetDistance

        // Create a new pose for the offset position
        val offsetPose = Pose.makeTranslation(offsetX, offsetY, offsetZ)

        // Create a new anchor at the offset position
        val offsetAnchor = session.createAnchor(offsetPose)

        // Create a layer label at the offset position
        return layerManager.addLayerLabel(session, offsetAnchor, mockPlaceData)
    }

    private fun fetchAndUpdateSiteDetails(
        anchor: ARLabeledAnchor,
        siteName: String,
        siteId: String
    ) {
        Log.d(TAG, "Fetching site details for ID: $siteId")
        val context = activity.applicationContext
        val repository = SiteDetailsRepository(context)

        // Use the callback-based method instead of the suspend method
        repository.fetchSiteDetails(
            siteId = siteId,
            onSuccess = { siteInfo ->
                updateAnchorWithDetails(anchor, siteName, siteInfo, siteId)
            },
            onError = {
                Log.e(TAG, "Failed to fetch site details")
            }
        )
    }

    private fun isValidSiteData(siteInfo: SiteInformation?, siteId: String?): Boolean {
        if (siteInfo?.x == null || siteInfo.y == null || siteInfo.siteName == null || siteId.isNullOrBlank()) {
            Log.e(TAG, "Missing location data or site name/id")
            return false
        }
        return true
    }

    private fun createAnchor(
        session: Session,
        cameraPose: Pose,
        atX: Float,
        atY: Float,
        frame: Frame
    ): Anchor? {
        return placeLabelAccurateWithSnapshot(session, cameraPose, atX, atY, frame).also {
            if (it == null) {
                Log.e(TAG, "Failed to place anchor")
            }
        }
    }

    private fun updateAnchorWithDetails(
        oldAnchor: ARLabeledAnchor,
        siteName: String,
        siteInfo: SiteDetails,
        siteId: String
    ) {
        val previewText = extractPreviewText(siteInfo.description)
        val newLabelText = "$siteName||$previewText"

        Log.d(TAG, "Updated label with description: $newLabelText")

        synchronized(arLabeledAnchors) {
            val index = arLabeledAnchors.indexOf(oldAnchor)
            if (index != -1) {
                val updatedAnchor =
                    ARLabeledAnchor(oldAnchor.anchor, newLabelText, siteName).apply {
                        this.fullDescription = siteInfo.description
                        this.siteId = siteId
                    }
                arLabeledAnchors[index] = updatedAnchor
            }
        }
    }


    // Helper function to extract a single line or sentence for preview
    private fun extractPreviewText(description: String): String {
        if (description.isEmpty()) return ""

        // First try to get the first sentence
        val firstSentenceEnd = description.indexOfAny(charArrayOf('.', '!', '?'), 0)

        // If we found a sentence ending
        if (firstSentenceEnd >= 0 && firstSentenceEnd < 200) {
            // Return the complete sentence including the punctuation
            return description.substring(0, firstSentenceEnd + 1).trim()
        }

        // If no sentence end found, look for a line break
        val firstLineEnd = description.indexOf('\n')
        if (firstLineEnd >= 0 && firstLineEnd < 200) {
            return description.substring(0, firstLineEnd).trim()
        }

        // If the description is very long with no sentence breaks, take up to 200 chars
        if (description.length > 200) {
            // Find the last space before 200 characters to avoid cutting words
            val lastSpace = description.substring(0, 200).lastIndexOf(' ')
            return if (lastSpace > 0) {
                description.substring(0, lastSpace).trim() + "..."
            } else {
                // If no space found, cut at 200
                description.substring(0, 200).trim() + "..."
            }
        }

        // Otherwise return the whole description
        return description.trim()
    }

    private fun handleAnchorClick(clickedAnchor: ARLabeledAnchor) {
        // Use the siteName directly if available, otherwise extract from the label
        val siteId = clickedAnchor.siteId
        val siteName = clickedAnchor.siteName
        Log.d(TAG, "Clicked on anchor: $siteId")

        // Pass the full description to DetailActivity if available
        activity.runOnUiThread {
            activity.findViewById<View>(R.id.cameraButtonContainer)?.visibility = View.GONE
            // Show site details as an overlay instead of starting a new activity
            if (siteId != null) {
                view.showSiteDetails(siteId, clickedAnchor.fullDescription, siteName)
            }
        }
    }


    private fun getAllAnchors() {
        Model.shared.getAlArLabeledAnchors { fetchedAnchors ->
            synchronized(arLabeledAnchors) {
                arLabeledAnchors.clear()
                arLabeledAnchors.addAll(fetchedAnchors)
            }
        }
    }

    private fun addAnchorToDatabase(anchor: ARLabeledAnchor) {
        Model.shared.addArLabelAnchor(anchor) {
            getAllAnchors()
        }
    }

    fun getAnalyzedResult(path: String) {
        Log.d("AnalyzeImage", "Starting image analysis")

        launch(Dispatchers.IO) {
            val repository = DetectionResultRepository()
            val result = repository.getAnalyzedResult(path)

            withContext(Dispatchers.Main) {
                result.onSuccess { analyzedResult ->
                    if (analyzedResult.status == "failure") {
                        Log.d("AnalyzeImage", "No objects detected: ${analyzedResult.description}")
                        showSnackbar("No objects detected in the image")
                        serverResult = null
                    } else {
                        Log.d(
                            "AnalyzeImage",
                            "Site detected: ${analyzedResult.siteInformation?.siteName ?: "Unknown"}"
                        )
                        serverResult = analyzedResult
                    }
                }

                result.onFailure { error ->
                    Log.e("AnalyzeImage", "Error analyzing image: ${error.localizedMessage}")
                    showSnackbar("Error analyzing the image: ${error.message}")
                }
            }
        }
    }

    fun getNearbyPlacesForAR(categories: List<String>) {
        Log.d("NearbyPlaces", "Fetching nearby places for AR...")

        launch(Dispatchers.IO) {
            val currentLocation = geoLocationUtils.getSingleCurrentLocation()
                ?: return@launch
            geoLocationUtils.updateLocation(currentLocation)
            val repository = NearbyPlacesRepository()
            val result = repository.fetchNearbyPlaces(
                currentLocation.latitude,
                currentLocation.longitude,
                categories
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { places ->
                    Log.d("NearbyPlaces", "Received ${places.size} places")
                    showSnackbar("Received ${places.size} places")
                    showSnackbar("Received ${places[0].name} , ${places[1].name} places")
                    //updating AR nearbyPlaces anchors when creates
                    // updateARViewWithPlaces(places)
                }

                result.onFailure { error ->
                    Log.e("NearbyPlaces", "Error fetching places: ${error.localizedMessage}")
                    showSnackbar("Couldn't load nearby places: ${error.message}")
                }
            }
        }
    }

    private fun createSiteHistoryForDetectedObject(
        result: ImageAnalyzedResult,
        location: Location?
    ) {
        launch {
            val currentLocation = geoLocationUtils.getSingleCurrentLocation()
                ?: return@launch

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
