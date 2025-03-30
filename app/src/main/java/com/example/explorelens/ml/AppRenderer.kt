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

    private fun drawAnchors(render: SampleRender, frame: Frame){

        for (arDetectedObject in arLabeledAnchors) {
            val anchor = arDetectedObject.anchor

            Log.d(TAG, "Anchor tracking state111: ${anchor.trackingState}")
            Log.d(TAG, "Label222: ${arDetectedObject.label}")
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
        val objects = objectResults
        if (objects != null) {
            objectResults = null
            val snapshotData = lastSnapshotData

            Log.i(TAG, "$currentAnalyzer got objects: $objects")
            val anchors = objects.mapNotNull { obj ->
                val (atX, atY) = obj.centerCoordinate
                val snapshotData = lastSnapshotData

                if (snapshotData == null) {
                    Log.e(TAG, "No snapshot data available for anchor creation")
                    return@mapNotNull null
                }

                val anchor = createAnchorAtPoseXY(
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

        // Process any pending touch events
        pendingTouchX?.let { x ->
            pendingTouchY?.let { y ->
                processTouchInGLThread(x, y, frame, session)
                // Clear the pending touch
                pendingTouchX = null
                pendingTouchY = null
            }
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
                objectResults = mockResults
            }
        }

        processObjectResults(frame, session)
        drawAnchors(render, frame)
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
                    Log.d(TAG, "Clicked on anchor: ${clickedAnchor.label}")
                    activity.runOnUiThread {
                        // Navigate to detail activity on the UI thread
                        openDetailActivity(clickedAnchor.label)
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