package com.example.explorelens.ml

import android.content.Intent
import android.graphics.Bitmap
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.DetailActivity
import com.example.explorelens.Extensions.convertYuv
import com.example.explorelens.Extensions.toFile
import com.example.explorelens.Model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.arcore.BackgroundRenderer
import com.example.explorelens.ml.classification.utils.ImageUtils
import com.example.explorelens.ml.render.LabelRender
import com.example.explorelens.ml.render.PointCloudRender
import com.example.explorelens.networking.allImageAnalyzedResults
import com.example.explorelens.networking.ImageAnalyzedResult
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
import com.example.explorelens.networking.AnalyzedResultApi
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.lang.Thread.sleep
import java.util.Collections
import kotlin.math.sqrt

class AppRenderer(val activity: MainActivity) : DefaultLifecycleObserver, SampleRender.Renderer,
    CoroutineScope by MainScope() {
    companion object {
        val TAG = "HelloArRenderer"
    }

    lateinit var view: MainActivityView
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

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    fun bindView(view: MainActivityView) {
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
        processObjectResults(frame, session)
        drawAnchors(render, frame)
    }

    private fun drawAnchors(render: SampleRender, frame: Frame) {

        for (arDetectedObject in arLabeledAnchors) {
            val anchor = arDetectedObject.anchor

            Log.d(TAG, "Anchor tracking state: ${anchor.trackingState}")
            Log.d(TAG, "Label: ${arDetectedObject.label}")
            if (anchor.trackingState != TrackingState.TRACKING) continue

            val containerWidth = 249.0f
            val containerHeight = 80.0f
            val containerPose = anchor.pose

            labelRenderer.drawContainerSimple(
                render,
                viewProjectionMatrix,
                containerWidth,
                containerHeight
            )

            labelRenderer.drawContainer(
                render,
                viewProjectionMatrix,
                containerPose,
                containerWidth,
                containerHeight
            )
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

                val anchor = placeLabelRelativeToSnapshotWithYCorrection(
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
                ARLabeledAnchor(anchor, obj.siteInformation!!.siteName)

            }
            arLabeledAnchors.addAll(anchors)
            view.post {
                view.setScanningActive(false)
                when {

                    anchors.size != objects.size ->
                        showSnackbar(
                            "Objects were classified, but could not be attached to an anchor. " +
                                    "Try moving your device around to obtain a better understanding of the environment."
                        )
                }
            }
        }
    }
    private fun placeLabelRelativeToSnapshotWithYCorrection(
        session: Session,
        snapshotPose: Pose,
        labelX: Float,
        labelY: Float,
        frame: Frame
    ): Anchor? {
        // Convert normalized coordinates (0.5, 0.5) to image pixel coordinates
        val imageWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val imageHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        // If server is sending normalized coordinates (0-1), convert to pixels
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
            hitPose.ty() - 0.2f, // Lower by 20cm to fix the "too high" issue
            hitPose.tz()
        )

        // Create the anchor with the adjusted position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])

        Log.d(TAG, "Created anchor at x=${anchorPose.tx()}, y=${anchorPose.ty()}, z=${anchorPose.tz()} from normalized (${labelX}, ${labelY})")

        return session.createAnchor(anchorPose)
    }

    private fun placeLabelRelativeToSnapshot(
        session: Session,
        snapshotPose: Pose,
        labelX: Float,  // This is in normalized coordinates (0.0 to 1.0)
        labelY: Float,  // This is in normalized coordinates (0.0 to 1.0)
        frame: Frame
    ): Anchor? {
        // Log input coordinates
        Log.d(TAG, "Input normalized coordinates: x=$labelX, y=$labelY")

        // 1. Convert normalized coordinates (0.0 to 1.0) to image pixel coordinates
        val imageWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val imageHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        val pixelX = labelX * imageWidth  // Convert normalized x to pixel x
        val pixelY = labelY * imageHeight  // Convert normalized y to pixel y

        // 2. Convert image coordinates (pixelX, pixelY) to view coordinates
        convertFloats[0] = pixelX
        convertFloats[1] = pixelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // 3. Perform a hit test at the converted view coordinates
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull()
        if (hitResult == null) {
            Log.e(TAG, "No hit test results found")
            return null
        }

        // 4. Get the hit pose and distance
        val hitPose = hitResult.hitPose
        val hitDistance = hitResult.distance
        Log.d(TAG, "Hit pose: tx=${hitPose.tx()}, ty=${hitPose.ty()}, tz=${hitPose.tz()}, distance=$hitDistance")

        // 5. Calculate a ray from the current camera through the touch point
        val viewWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val viewHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        val normalizedX = (pixelX / viewWidth) * 2 - 1  // Convert to normalized device coordinates
        val normalizedY = -((pixelY / viewHeight) * 2 - 1) // Flip Y axis

        // 6. Create the same ray direction but from the snapshot camera's perspective
        val rayDirection = floatArrayOf(normalizedX, normalizedY, -1.0f, 0.0f)

        // 7. Transform this direction from snapshot camera space to world space
        val snapshotRayWorldDirection = snapshotPose.transformPoint(rayDirection)

        // 8. Apply the hit distance to the ray from the snapshot camera's position
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + snapshotRayWorldDirection[0] * hitDistance * 0.95f, // Apply correction
            snapshotPose.ty() + snapshotRayWorldDirection[1] * hitDistance * 0.95f,
            snapshotPose.tz() + snapshotRayWorldDirection[2] * hitDistance * 0.95f
        )

        // 9. Create an anchor at this computed world position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])
        Log.d(TAG, "Final anchor pose: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}")

        return session.createAnchor(anchorPose)
    }

    private fun placeLabelRelativeToSnapshot1(
        session: Session,
        snapshotPose: Pose,
        labelX: Float,
        labelY: Float,
        frame: Frame
    ): Anchor? {
        // Log input coordinates
        Log.d(TAG, "Input image coordinates: x=$labelX, y=$labelY")

        // 1. Convert image coordinates to view coordinates
        convertFloats[0] = labelX
        convertFloats[1] = labelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // 2. Get current camera pose for reference
        val currentCameraPose = frame.camera.pose

        // 3. Perform hit test using current camera
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull()
        if (hitResult == null) {
            Log.e(TAG, "No hit test results found")
            return null
        }

        // 4. Get hit pose and distance
        val hitPose = hitResult.hitPose
        val hitDistance = hitResult.distance
        Log.d(TAG, "Hit pose: tx=${hitPose.tx()}, ty=${hitPose.ty()}, tz=${hitPose.tz()}, distance=$hitDistance")

        // 5. Calculate a ray from the current camera through the touch point
        val viewWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val viewHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()
        val normalizedX = (labelX / viewWidth) * 2 - 1
        val normalizedY = -((labelY / viewHeight) * 2 - 1) // Flip Y axis

        // 6. Create the same ray direction but from the snapshot camera's perspective
        // This is key - we're using the same viewport coordinates but from the snapshot pose
        val rayDirection = floatArrayOf(normalizedX, normalizedY, -1.0f, 0.0f)

        // 7. Transform this direction from snapshot camera space to world space
        val snapshotRayWorldDirection = snapshotPose.transformPoint(rayDirection)

        // 8. Use the hit distance from the current camera's hit test
        // but apply it to the ray from the snapshot camera's position
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + snapshotRayWorldDirection[0] * hitDistance * 0.95f, // Apply a small correction factor
            snapshotPose.ty() + snapshotRayWorldDirection[1] * hitDistance * 0.95f, // to address the "too high" issue
            snapshotPose.tz() + snapshotRayWorldDirection[2] * hitDistance * 0.95f
        )

        // 9. Create an anchor at this computed position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])
        Log.d(TAG, "Final anchor: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}")

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
        path?.let {
            Log.d("Snapshot", "Calling getAnalyzedResult with path: $it")
            getAnalyzedResult(it)
        }
        val snapshot = Snapshot(
            timestamp = frame.timestamp,
            cameraPose = camera.pose,
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix
        )

        launch(Dispatchers.Main) {
            view.setScanningActive(false)
        }

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
        Log.e("IM HERE", "HERE")
        launch(Dispatchers.IO) {
            try {
                val file = File(path)
                Log.d("TAG", "Fetching analyzed result for image: ${file.path}")

                val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
                val multipartBody = MultipartBody.Part.createFormData("image", file.name, requestBody)
                val request = AnalyzedResultsClient.analyzedResultApiClient.getAnalyzedResult(multipartBody)
                val response = request.execute()

                Log.d("TAG", "Response: ${response}")

                if (response.isSuccessful) {
                    val result: allImageAnalyzedResults? = response.body()

                    if (result?.result.isNullOrEmpty() && result?.status != null) {
                        // Single direct response format - create an ImageAnalyzedResult
                        val singleResult = ImageAnalyzedResult(
                            status = result.status ?: "",
                            description = result.description ?: "",
                            siteInformation = result.siteInformation
                        )

                        launch(Dispatchers.Main) {
                            // Check if the status is "failure" and show snackbar without processing anchors
                            if (singleResult.status == "failure" && singleResult.description == "No famous site detected and no relevant objects found.") {
                                showSnackbar("No objects detected in the image")
                                return@launch // Early return to stop further processing
                            }

                            serverResult = listOf(singleResult)
                            Log.d("Snapshot", "Site detected: ${singleResult.siteInformation?.siteName ?: "Unknown"}")
                        }
                    } else {
                        // Original format with result array
                        Log.d("TAG", "Fetched analyzed result! Total objects: ${result?.result?.size ?: 0}")
                        val objects = result?.result ?: emptyList()

                        launch(Dispatchers.Main) {
                            serverResult = objects
                            if (serverResult?.isNotEmpty() == true) {
                                val firstObject = serverResult?.firstOrNull()
                                Log.d("Snapshot", "Image analyzed: ${firstObject?.siteInformation?.siteName ?: "No label found"} - ${firstObject?.siteInformation?.siteName ?: "No site name"}")
                            } else {
                                Log.d("Snapshot", "No objects detected in the image")
                                showSnackbar("No objects detected in the image")
                            }
                        }
                    }
                } else {
                    Log.e("TAG", "Failed to analyze result! Response code: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("TAG", "Failed to analyze result! Exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }



    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    private fun createAnchorAtPoseXY(
        session: Session,
        snapshotPose: Pose,
        xImage: Float,
        yImage: Float,
        frame: Frame
    ): Anchor? {
        // Log input coordinates
        Log.d(TAG, "Input image coordinates: x=$xImage, y=$yImage")

        // Transform image coordinates to view coordinates
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Log transformed coordinates
        Log.d(
            "TAG",
            "Transformed view coordinates: x=${convertFloatsOut[0]}, y=${convertFloatsOut[1]}"
        )

        // Log snapshot pose details
        Log.d(
            "TAG", "Snapshot Pose: " +
                    "tx=${snapshotPose.tx()}, " +
                    "ty=${snapshotPose.ty()}, " +
                    "tz=${snapshotPose.tz()}"
        )

        // Perform hit test
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull()

        if (hitResult == null) {
            Log.e("TAG", "No hit test results found")
            return null
        }

        // Log hit pose details
        val hitPose = hitResult.hitPose
        Log.d(
            "TAG", "Hit Pose: " +
                    "tx=${hitPose.tx()}, " +
                    "ty=${hitPose.ty()}, " +
                    "tz=${hitPose.tz()}"
        )

        // Create anchor at hit pose
        return session.createAnchor(hitPose)
    }


    private fun showSnackbar(message: String): Unit =
        activity.view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

    fun handleTouch(x: Float, y: Float) {
        val session = activity.arCoreSessionHelper.sessionCache ?: return
        val frame = session.update()

        val hitResults = frame.hitTest(x, y)  // Perform a hit test
        for (hit in hitResults) {
            val hitPose = hit.hitPose
            val clickedAnchor = arLabeledAnchors.find { anchor ->
                val distance = distanceBetween(anchor.anchor.pose, hitPose)
                distance < 0.1f // Threshold distance (adjust as needed)
            }

            if (clickedAnchor != null) {
                openDetailActivity(clickedAnchor.label)
                return
            }
        }
    }
    private fun openDetailActivity(label: String) {
        val intent = Intent(activity, DetailActivity::class.java)
        intent.putExtra("LABEL_KEY", label)
        activity.startActivity(intent)
    }
    private fun distanceBetween(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }



}

data class ARLabeledAnchor(val anchor: Anchor, val label: String)