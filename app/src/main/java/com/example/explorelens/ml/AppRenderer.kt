package com.example.explorelens.ml

import android.graphics.Bitmap
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.Extensions.convertYuv
import com.example.explorelens.Extensions.toFile
import com.example.explorelens.Model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.arcore.BackgroundRenderer
import com.example.explorelens.ml.classification.DetectedObjectResult
import com.example.explorelens.ml.classification.MLKitObjectDetector
import com.example.explorelens.ml.classification.ObjectDetector
import com.example.explorelens.ml.classification.utils.ImageUtils
import com.example.explorelens.ml.render.LabelRender
import com.example.explorelens.ml.render.PointCloudRender
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

    var scanButtonWasPressed = false

    private var lastSnapshotData: Snapshot? = null
    val mlKitAnalyzer = MLKitObjectDetector(activity)
    var currentAnalyzer: ObjectDetector = mlKitAnalyzer
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

    var objectResults: List<DetectedObjectResult>? = null

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

        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera is not tracking.")
            return
        }

        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }

        if (scanButtonWasPressed) {
            scanButtonWasPressed = false
            lastSnapshotData = takeSnapshot(frame, session)
            launch(Dispatchers.IO) {
                val mockResults = listOf(
                    DetectedObjectResult(
                        confidence = 0.98f,
                        label = "Eiffel Tower",
                        centerCoordinate = Pair(300, 700)  // Mock coordinates
                    ),
                    DetectedObjectResult(
                        confidence = 0.87f,
                        label = "London eye",
                        centerCoordinate = Pair(200, 500)  // Mock coordinates
                    )
                )
                sleep(2000)
                objectResults = mockResults
            }
        }

        processObjectResults(frame, session)
        drawAnchors(render, frame)
    }


    private fun drawAnchors(render: SampleRender, frame: Frame){

        for (arDetectedObject in arLabeledAnchors) {
            val anchor = arDetectedObject.anchor

            Log.d(TAG, "Anchor tracking state111: ${anchor.trackingState}")
            Log.d(TAG, "Label222: ${arDetectedObject.label}")
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
        val objects = objectResults
        if (objects != null) {
            objectResults = null
            val snapshotData = lastSnapshotData

            Log.i(TAG, "$currentAnalyzer got objects: $objects")
            val anchors = objects.mapNotNull { obj ->
                val (atX, atY) = obj.centerCoordinate

                if (snapshotData == null) {
                    Log.e(TAG, "No snapshot data available for anchor creation")
                    return@mapNotNull null
                }

                val anchor = placeLabelRelativeToSnapshotWithYCorrection(
                    session,
                    snapshotData.cameraPose,
                    atX.toFloat(),
                    atY.toFloat(),
                    frame
                ) ?: return@mapNotNull null

                Log.d(TAG, "Anchor created for ${obj.label}")
                Log.d(TAG, "Anchor Pose: x=${anchor.pose.tx()}, y=${anchor.pose.ty()}, z=${anchor.pose.tz()}")

                ARLabeledAnchor(anchor, obj.label)
            }
            arLabeledAnchors.addAll(anchors)
            view.post {
                view.setScanningActive(false)
                when {
                    objects.isEmpty() && currentAnalyzer == mlKitAnalyzer && !mlKitAnalyzer.hasCustomModel() ->
                        showSnackbar("Default ML Kit classification model returned no results. " +
                                "For better classification performance, see the README to configure a custom model.")
                    objects.isEmpty() ->
                        showSnackbar("Classification model returned no results.")
                    anchors.size != objects.size ->
                        showSnackbar("Objects were classified, but could not be attached to an anchor. " +
                                "Try moving your device around to obtain a better understanding of the environment.")
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
        // Same initial steps as before for coordinate conversion
        convertFloats[0] = labelX
        convertFloats[1] = labelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Hit test
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull() ?: return null

        // Get the hit pose
        val hitPose = hitResult.hitPose

        // Create ray direction from snapshot
        val viewWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val viewHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()
        val normalizedX = (labelX / viewWidth) * 2 - 1
        val normalizedY = -((labelY / viewHeight) * 2 - 1)

        // Create direction vector
        val rayDirection = floatArrayOf(normalizedX, normalizedY, -1.0f, 0.0f)
        val snapshotRayDirection = snapshotPose.transformPoint(rayDirection)

        // Get distance
        val hitDistance = hitResult.distance

        // Calculate world position from snapshot
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + snapshotRayDirection[0] * hitDistance,
            // Explicitly lower the Y position to address the "too high" issue
            (snapshotPose.ty() + snapshotRayDirection[1] * hitDistance) - 0.2f, // Subtract 20cm
            snapshotPose.tz() + snapshotRayDirection[2] * hitDistance
        )

        // Create the anchor at the adjusted position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])

        return session.createAnchor(anchorPose)
    }

    private fun placeLabelRelativeToSnapshot(
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
        var rotatedImage: Bitmap? = null;
        try {

            val cameraImage = frame.tryAcquireCameraImage()
            if (cameraImage != null) {
                val cameraId = session.cameraConfig.cameraId
                val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
                val convertYuv = convertYuv(context, cameraImage)
                rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)
                val file = rotatedImage.toFile(context, "snapshot")
                Log.d("Snapshot", "Image saved at: ${file.absolutePath}")
                cameraImage.close()
            }
        } catch (e: NotYetAvailableException) {
            Log.e("takeSnapshot", "No image available yet")
        }

        val snapshot = Snapshot(
            image = rotatedImage,
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
        Log.d(TAG, "Transformed view coordinates: x=${convertFloatsOut[0]}, y=${convertFloatsOut[1]}")

        // Log snapshot pose details
        Log.d(TAG, "Snapshot Pose: " +
                "tx=${snapshotPose.tx()}, " +
                "ty=${snapshotPose.ty()}, " +
                "tz=${snapshotPose.tz()}")

        // Perform hit test
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val hitResult = hits.firstOrNull()

        if (hitResult == null) {
            Log.e(TAG, "No hit test results found")
            return null
        }

        // Log hit pose details
        val hitPose = hitResult.hitPose
        Log.d(TAG, "Hit Pose: " +
                "tx=${hitPose.tx()}, " +
                "ty=${hitPose.ty()}, " +
                "tz=${hitPose.tz()}")

        // Create anchor at hit pose
        return session.createAnchor(hitPose)
    }


    private fun showSnackbar(message: String): Unit =
        activity.view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

}

data class ARLabeledAnchor(val anchor: Anchor, val label: String)