package com.example.explorelens.ar.components

import android.opengl.Matrix
import android.util.Log
import com.example.explorelens.ArActivity
import com.example.explorelens.ar.ARLayerManager
import com.example.explorelens.ar.render.LabelRender
import com.example.explorelens.ar.render.PointCloudRender
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.arcore.BackgroundRenderer
import com.example.explorelens.model.ARLabeledAnchor
import com.example.explorelens.model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException


class ARSceneRenderer(
    private val activity: ArActivity,
    private val displayRotationHelper: DisplayRotationHelper
) {
    companion object {
        private const val TAG = "ARSceneRenderer"
        private const val NEAR_PLANE = 0.01f
        private const val FAR_PLANE = 100.0f
    }

    private lateinit var backgroundRenderer: BackgroundRenderer
    private val pointCloudRender = PointCloudRender()
    private val labelRenderer = LabelRender()
    private val layerManager = ARLayerManager(activity)

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)

    private var isInitialized = false

    fun onSurfaceCreated(render: SampleRender) {
        backgroundRenderer = BackgroundRenderer(render).apply {
            setUseDepthVisualization(render, false)
        }
        pointCloudRender.onSurfaceCreated(render)
        labelRenderer.onSurfaceCreated(render, activity)
        layerManager.onSurfaceCreated(render)
        isInitialized = true
        Log.d(TAG, "AR Scene Renderer initialized")
    }

    fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    fun drawFrame(
        render: SampleRender,
        frame: Frame,
        anchors: List<ARLabeledAnchor>,
        lastSnapshotData: Snapshot?
    ): RenderResult {
        if (!isInitialized) {
            return RenderResult.NotInitialized
        }

        val session = activity.arCoreSessionHelper.sessionCache
            ?: return RenderResult.NoSession

        try {
            // Setup camera and matrices
            setupCameraAndMatrices(session, frame)

            // Draw background camera feed
            backgroundRenderer.updateDisplayGeometry(frame)
            backgroundRenderer.drawBackground(render)

            val camera = frame.camera

            // Check if camera is tracking
            if (camera.trackingState != TrackingState.TRACKING) {
                Log.w(TAG, "Camera is not tracking")
                return RenderResult.CameraNotTracking
            }

            // Draw point cloud
            drawPointCloud(render, frame)

            // Draw AR anchors/labels
            drawAnchors(render, anchors, lastSnapshotData, frame)

            // Draw layer-based content (geo-anchored places)
            drawLayerContent(render, camera, frame)

            return RenderResult.Success

        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during rendering", e)
            return RenderResult.CameraError(e.message ?: "Camera not available")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during rendering", e)
            return RenderResult.UnknownError(e.message ?: "Unknown rendering error")
        }
    }

    private fun setupCameraAndMatrices(session: com.google.ar.core.Session, frame: Frame) {
        session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
        displayRotationHelper.updateSessionIfNeeded(session)

        val camera = frame.camera
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, NEAR_PLANE, FAR_PLANE)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

    }

    private fun drawPointCloud(render: SampleRender, frame: Frame) {
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }
    }

    private fun drawAnchors(
        render: SampleRender,
        anchors: List<ARLabeledAnchor>,
        lastSnapshotData: Snapshot?,
        frame: Frame
    ) {
        for (arDetectedObject in anchors) {
            val anchor = arDetectedObject.anchor

            if (anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            Log.d(TAG, "Rendering anchor - State: ${anchor.trackingState}, Label: ${arDetectedObject.label}")

            labelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchor.pose,
                lastSnapshotData?.cameraPose ?: frame.camera.pose,
                arDetectedObject.label
            )
        }
    }

    private fun drawLayerContent(render: SampleRender, camera: com.google.ar.core.Camera, frame: Frame) {
        layerManager.drawLayerLabels(render, viewProjectionMatrix, camera.pose, frame)
    }

    fun getLayerManager(): ARLayerManager = layerManager

    sealed class RenderResult {
        object Success : RenderResult()
        object NotInitialized : RenderResult()
        object NoSession : RenderResult()
        object CameraNotTracking : RenderResult()
        data class CameraError(val message: String) : RenderResult()
        data class UnknownError(val message: String) : RenderResult()
    }
}