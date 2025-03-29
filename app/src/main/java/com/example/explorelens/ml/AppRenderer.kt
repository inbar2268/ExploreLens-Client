package com.example.explorelens.ml

import android.opengl.Matrix
import android.os.SystemClock.sleep
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.arcore.BackgroundRenderer
import com.example.explorelens.ml.classification.DetectedObjectResult
import com.example.explorelens.ml.classification.GoogleCloudVisionDetector
import com.example.explorelens.ml.classification.MLKitObjectDetector
import com.example.explorelens.ml.classification.ObjectDetector
import com.example.explorelens.ml.render.LabelRender
import com.example.explorelens.ml.render.PointCloudRender
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.google.ar.core.Pose // Import Pose for position manipulation
import com.google.ar.core.Session

class AppRenderer(val activity: MainActivity) : DefaultLifecycleObserver, SampleRender.Renderer, CoroutineScope by MainScope() {
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

    val arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
    var scanButtonWasPressed = false
    var renderBehindMeButtonPressed = false // Add this line

    val mlKitAnalyzer = MLKitObjectDetector(activity)
    val gcpAnalyzer = GoogleCloudVisionDetector(activity)

    var currentAnalyzer: ObjectDetector = gcpAnalyzer
    private var lastSnapshotData: SnapshotData? = null


    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    fun bindView(view: MainActivityView) {
        this.view = view

        view.scanButton.setOnClickListener {
            scanButtonWasPressed = true
            view.setScanningActive(true)
            hideSnackbar()
        }

        view.useCloudMlSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentAnalyzer = if (isChecked) gcpAnalyzer else mlKitAnalyzer
        }

        val gcpConfigured = gcpAnalyzer.credentials != null
        view.useCloudMlSwitch.isChecked = gcpConfigured
        view.useCloudMlSwitch.isEnabled = gcpConfigured
        currentAnalyzer = if (gcpConfigured) gcpAnalyzer else mlKitAnalyzer

        if (!gcpConfigured) {
            showSnackbar("Google Cloud Vision isn't configured (see README). The Cloud ML switch will be disabled.")
        }

        view.resetButton.setOnClickListener {
            arLabeledAnchors.clear()
            view.resetButton.isEnabled = false
            hideSnackbar()
        }

        view.placeBehindMeButton.setOnClickListener { // Add this block
            renderBehindMeButtonPressed = true
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
            Log.w(TAG, "Camera is not tracking. Unable to place anchors.")
            return
        }

        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }

        if (scanButtonWasPressed) {
            scanButtonWasPressed = false
            val cameraImage = frame.tryAcquireCameraImage()
            if (cameraImage != null) {
                val cameraId = session.cameraConfig.cameraId
                val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)

                lastSnapshotData = SnapshotData(
                    viewMatrix = viewMatrix.copyOf(),
                    projectionMatrix = projectionMatrix.copyOf(),
                    cameraPose = camera.pose,
                    imageRotation = imageRotation
                )

                launch(Dispatchers.IO) {
                    sleep(2000)
                    objectResults = currentAnalyzer.analyze(cameraImage, imageRotation)
                    cameraImage.close()
                }
            }
        }

        val objects = objectResults
        if (objects != null) {
            objectResults = null
            val snapshotData = lastSnapshotData
            val cameraPose = snapshotData?.cameraPose ?:  frame.camera.pose// Get the camera pose from the frame

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
                view.resetButton.isEnabled = arLabeledAnchors.isNotEmpty()
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

        if (renderBehindMeButtonPressed) {
            renderBehindMeButtonPressed = false
            val cameraPose = camera.pose
            val behindCamera = cameraPose.compose(Pose.makeTranslation(0f, 0f, 1f)) // Corrected: positive Z for behind.
            val anchor = session.createAnchor(behindCamera)
            arLabeledAnchors.add(ARLabeledAnchor(anchor, "Behind Me"))
            view.post {
                view.resetButton.isEnabled = arLabeledAnchors.isNotEmpty()
            }
        }

        for (arDetectedObject in arLabeledAnchors) {
            val anchor = arDetectedObject.anchor

            Log.d(TAG, "Anchor tracking state111: ${anchor.trackingState}")
            Log.d(TAG, "Label222: ${arDetectedObject.label}")
            if (anchor.trackingState != TrackingState.TRACKING) continue
            labelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchor.pose,
                camera.pose,
                arDetectedObject.label
            )
        }
    }

    fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }

    private fun showSnackbar(message: String): Unit =
        activity.view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val result = hits.getOrNull(0) ?: return null
        return result.trackable.createAnchor(result.hitPose)
    }
    private fun createAnchorAtPoseXY2(
        session: Session,
        snapshotPose: Pose,
        xImage: Float,
        yImage: Float,
        frame: Frame
    ): Anchor? {
        // Transform image coordinates to view coordinates
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Create a translation vector based on the view coordinates
        val translationVector = FloatArray(3)

        // Use a smaller scaling factor to keep the object closer
        val scaleFactor = 0.3f
        translationVector[0] = convertFloatsOut[0] * scaleFactor
        translationVector[1] = convertFloatsOut[1] * scaleFactor
        translationVector[2] = -0.5f  // Adjust depth placement

        // Create a new pose by combining the snapshot pose with the translation
        val anchorPose = snapshotPose.compose(
            Pose.makeTranslation(translationVector[0], translationVector[1], translationVector[2])
        )

        // Logging to understand the positioning
        Log.d(TAG, "Snapshot Pose: x=${snapshotPose.tx()}, y=${snapshotPose.ty()}, z=${snapshotPose.tz()}")
        Log.d(TAG, "Translation Vector: x=${translationVector[0]}, y=${translationVector[1]}, z=${translationVector[2]}")
        Log.d(TAG, "Resulting Anchor Pose: x=${anchorPose.tx()}, y=${anchorPose.ty()}, z=${anchorPose.tz()}")

        // Create and return the anchor
        return session.createAnchor(anchorPose)
    }

    private fun createAnchorAtPoseXY1(
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

    private fun createAnchorAtPoseXY(
        session: Session,
        snapshotPose: Pose,
        xImage: Float,
        yImage: Float,
        frame: Frame
    ): Anchor? {
        // Log the input image coordinates
        Log.d(TAG, "Input Image Coordinates: x=$xImage, y=$yImage")

        // Transform image coordinates to view coordinates
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        Log.d(TAG, "Transformed View Coordinates: x=${convertFloatsOut[0]}, y=${convertFloatsOut[1]}")

        // Create a more controlled translation based on the snapshot pose
        // Use the transformed view coordinates to create an offset
        val translationVector = FloatArray(3)

        // Scale factor to control distance from the snapshot camera
        val scaleFactor = 0.5f

        translationVector[0] = convertFloatsOut[0] * scaleFactor
        translationVector[1] = convertFloatsOut[1] * scaleFactor
        translationVector[2] = -0.5f  // Bring the object slightly closer

        // Create a new pose by combining the snapshot pose with the translation
        val anchorPose = snapshotPose.compose(
            Pose.makeTranslation(translationVector[0], translationVector[1], translationVector[2])
        )

        // Extensive logging for debugging
        Log.d(TAG, "Snapshot Pose: x=${snapshotPose.tx()}, y=${snapshotPose.ty()}, z=${snapshotPose.tz()}")
        Log.d(TAG, "Translation Vector: x=${translationVector[0]}, y=${translationVector[1]}, z=${translationVector[2]}")
        Log.d(TAG, "Resulting Anchor Pose: x=${anchorPose.tx()}, y=${anchorPose.ty()}, z=${anchorPose.tz()}")

        // Create and return the anchor
        return session.createAnchor(anchorPose)
    }
}



data class SnapshotData(
    val viewMatrix: FloatArray,
    val projectionMatrix: FloatArray,
    val cameraPose: Pose,
    val imageRotation: Int
)

data class ARLabeledAnchor(val anchor: Anchor, val label: String)