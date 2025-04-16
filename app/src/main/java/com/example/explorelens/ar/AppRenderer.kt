package com.example.explorelens.ar

import android.content.Intent
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.ArActivity
import com.example.explorelens.DetailActivity
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
import com.example.explorelens.data.network.allImageAnalyzedResults
import com.example.explorelens.data.network.ImageAnalyzedResult
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
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.example.explorelens.data.network.AnalyzedResultsClient
import com.example.explorelens.data.network.SiteInformation
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.lang.Thread.sleep
import java.util.Collections
import kotlin.math.sqrt
import com.example.explorelens.BuildConfig

class AppRenderer(val activity: ArActivity) : DefaultLifecycleObserver, SampleRender.Renderer,
    CoroutineScope by MainScope() {
    companion object {
        val TAG = "HelloArRenderer"
    }

    lateinit var view: ArActivityView
    val displayRotationHelper = DisplayRotationHelper(activity)
    lateinit var backgroundRenderer: BackgroundRenderer
    val pointCloudRender = PointCloudRender()
    val labelRenderer = LabelRender()

    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val viewProjectionMatrix = FloatArray(16)

    var serverResult: List<ImageAnalyzedResult>? = listOf()
    var scanButtonWasPressed = false

    private var lastSnapshotData: Snapshot? = null
    val arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())

    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    fun bindView(view: ArActivityView) {
        this.view = view
        view.snapshotButton.setOnClickListener {
            scanButtonWasPressed = true
            view.setScanningActive(true)
            hideSnackbar()
        }
    }

    override fun onSurfaceCreated(render: SampleRender) {
        backgroundRenderer = BackgroundRenderer(render).apply {
            setUseDepthVisualization(render, false)
        }
        pointCloudRender.onSurfaceCreated(render)
        labelRenderer.onSurfaceCreated(render)
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
    }

    private fun drawAnchors(render: SampleRender, frame: Frame) {

        for (arDetectedObject in arLabeledAnchors) {
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
        val objects = serverResult
        if (objects != null) {
            serverResult = null
            val snapshotData = lastSnapshotData

            Log.i(TAG, " Analyzer got objects: $objects")
            val anchors = objects.mapNotNull { obj ->
                val atX = obj.siteInformation?.x
                val atY = obj.siteInformation?.y

                if(atX == null || atY == null){
                    return@mapNotNull null
                }

                if (snapshotData == null) {
                    Log.e(TAG, "No snapshot data available for anchor creation")
                    return@mapNotNull null
                }

                val anchor = placeLabelAccurateWithSnapshot(
                    session,
                    snapshotData.cameraPose,
                    atX,
                    atY,
                    frame
                ) ?: return@mapNotNull null

                Log.d(TAG, "Anchor created for ${obj.siteInformation?.siteName}")
                Log.d(
                    TAG,
                    "Anchor Pose: x=${anchor.pose.tx()}, y=${anchor.pose.ty()}, z=${anchor.pose.tz()}"
                )

                // Create a combined label with site name and description
                // Format: "SiteName||Description"
                val siteName = obj.siteInformation!!.siteName
                val description = obj.description ?: ""
                val labelText = "$siteName||$description"

                Log.d(TAG, "Creating label with text: $labelText")

                ARLabeledAnchor(anchor, labelText)
            }

            arLabeledAnchors.addAll(anchors)
            view.post {
                view.setScanningActive(false)
                when {
                    anchors.isEmpty() ->
                        showSnackbar("No objects were detected. Try scanning again.")
                    anchors.size != objects.size ->
                        showSnackbar(
                            "Objects were classified, but could not be attached to an anchor. " +
                                    "Try moving your device around to obtain a better understanding of the environment."
                        )
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
        Log.d(TAG, "Snapshot pose: tx=${snapshotPose.tx()}, ty=${snapshotPose.ty()}, tz=${snapshotPose.tz()}")

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

        Log.d(TAG, "Created anchor at: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}")

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
        Log.d(TAG, "Snapshot pose: tx=${snapshotPose.tx()}, ty=${snapshotPose.ty()}, tz=${snapshotPose.tz()}")

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
            snapshotPose.ty() + normalizedDirection[1] * distance , // Lower by 20cm
            snapshotPose.tz() + normalizedDirection[2] * distance
        )

        // 6. Create anchor with final position
        val anchorPose = Pose.makeTranslation(
            worldPosition[0],
            worldPosition[1],
            worldPosition[2]
        )

        Log.d(TAG, "Created anchor at: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}")

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

        Log.d(TAG, "Created anchor at x=${anchorPose.tx()}, y=${anchorPose.ty()}, z=${anchorPose.tz()} from normalized (${labelX}, ${labelY})")

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

        if(BuildConfig.USE_MOCK_DATA){
            path?.let {
                Log.d("Snapshot", "Calling getAnalyzedResult with path: $it")
            }
            val mockResults = listOf(
            ImageAnalyzedResult(
                status = "success",
                description = "Famous site detected from cropped object.",
                siteInformation = SiteInformation(
                    label = "Building",
                    x = 0.4430117717f,
                    y = 0.419694645f,
                    siteName = "Independence Hall In Shalom Tower"
                )
            ),
            ImageAnalyzedResult(
                status = "assume",
                description = "Famous site detected in full image.",
                siteInformation = SiteInformation(
                    label = "full-image",
                    x = 0.5f,
                    y = 0.5f,
                    siteName = "Holocaust Monument Rabin Square"
                )
            )
        )
            launch(Dispatchers.Main) {
                sleep(2000)
                serverResult = mockResults
                view.setScanningActive(false)
            }
        } else{
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
    fun getAnalyzedResult(path: String) {
        Log.d("AnalyzeImage", "Starting image analysis")
        launch(Dispatchers.IO) {
            try {
                val file = File(path)
                Log.d("AnalyzeImage", "Analyzing image: ${file.path}")

                // Prepare multipart request with the image file
                val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
                val multipartBody = MultipartBody.Part.createFormData("image", file.name, requestBody)
                val request = AnalyzedResultsClient.analyzedResultApiClient.getAnalyzedResult(multipartBody)
                val response = request.execute()

                Log.d("AnalyzeImage", "Server response: ${response}")

                // Switch to Main thread for UI updates
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val result: allImageAnalyzedResults? = response.body()

                        // Check for null response body
                        if (result == null) {
                            Log.e("AnalyzeImage", "Received empty response from server")
                            showSnackbar("Server error: No data received")
                            return@withContext
                        }

                        // Check if this is a direct response (without results array)
                        if (result.result.isNullOrEmpty() && result.status != null) {
                            // Create a single result object from the direct response
                            val singleResult = ImageAnalyzedResult(
                                status = result.status ?: "",
                                description = result.description ?: "",
                                siteInformation = result.siteInformation
                            )

                            serverResult = listOf(singleResult)

                            // Handle failure status
                            if (singleResult.status == "failure") {
                                Log.d("AnalyzeImage", "No objects detected: ${singleResult.description}")
                                showSnackbar("No objects detected in the image")
                            } else {
                                Log.d("AnalyzeImage", "Site detected: ${singleResult.siteInformation?.siteName ?: "Unknown"}")
                            }
                        }
                        // Handle normal results array
                        else if (!result.result.isNullOrEmpty()) {
                            Log.d("AnalyzeImage", "Found ${result.result.size} objects in the image")
                            serverResult = result.result

                            val firstObject = serverResult?.firstOrNull()
                            if (firstObject != null) {
                                Log.d("AnalyzeImage", "Image analyzed: ${firstObject.siteInformation?.siteName ?: "No label found"}")
                            }
                        }
                        // Handle case with no results at all
                        else {
                            Log.d("AnalyzeImage", "No objects detected in the image")
                            showSnackbar("No objects detected in the image")
                            serverResult = emptyList()
                        }
                    } else {
                        // Handle unsuccessful HTTP response
                        Log.e("AnalyzeImage", "Analysis failed! Response code: ${response.code()}")
                        showSnackbar("Server error: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions during the request
                Log.e("AnalyzeImage", "Analysis failed! Exception: ${e.message}")
                e.printStackTrace()

                // Switch to Main thread to show error message
                withContext(Dispatchers.Main) {
                    showSnackbar("Error analyzing the image")
                }
            }
        }
    }


    private fun showSnackbar(message: String): Unit =
        activity.view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

    private var pendingTouchX: Float? = null
    private var pendingTouchY: Float? = null

    fun handleTouch(x: Float, y: Float) {
        // Just store the coordinates for later processing
        Log.d(TAG, "Touch received at x=$x, y=$y")
        pendingTouchX = x
        pendingTouchY = y
    }


    private fun openDetailActivity(label: String) {
        activity.runOnUiThread {
            Log.d(TAG, "Opening DetailActivity with label: $label")
            val intent = Intent(activity, DetailActivity::class.java)
            intent.putExtra("LABEL_KEY", label)
            activity.startActivity(intent)
        }
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
            // Perform hit test
            val hitResults = frame.hitTest(x, y)

            Log.d(TAG, "Hit test performed, found ${hitResults.size} results")

            for (hit in hitResults) {
                val hitPose = hit.hitPose
                Log.d(TAG, "Hit pose: x=${hitPose.tx()}, y=${hitPose.ty()}, z=${hitPose.tz()}")

                // Log all anchors for debugging
                arLabeledAnchors.forEachIndexed { index, anchor ->
                    Log.d(TAG, "Anchor $index: label=${anchor.label}, pose: x=${anchor.anchor.pose.tx()}, y=${anchor.anchor.pose.ty()}, z=${anchor.anchor.pose.tz()}")
                    val distance = distanceBetween(anchor.anchor.pose, hitPose)
                    Log.d(TAG, "Distance to anchor $index: $distance")
                }

                // Check if the hit is close to any of our anchors
                val clickedAnchor = arLabeledAnchors.find { anchor ->
                    val distance = distanceBetween(anchor.anchor.pose, hitPose)
                    Log.d(TAG, "Distance to ${anchor.label}: $distance")
                    distance < 0.2f  // You might need to adjust this threshold
                }

                if (clickedAnchor != null) {
                    // Extract only the site name part (before the || separator)
                    val siteName = clickedAnchor.label.split("||")[0]
                    Log.d(TAG, "Clicked on anchor: $siteName")

                    activity.runOnUiThread {
                        // Navigate to detail activity with just the site name
                        openDetailActivity(siteName)
                    }
                    return
                }
            }

            if (hitResults.isEmpty()) {
                Log.d(TAG, "No hit results found")
            } else {
                Log.d(TAG, "Hit results found but no anchors were close enough")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing touch", e)
        }
    }
}

data class ARLabeledAnchor(val anchor: Anchor, val label: String)

