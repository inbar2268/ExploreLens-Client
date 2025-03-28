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
import com.example.explorelens.ml.classification.utils.ImageUtils
import com.example.explorelens.ml.render.LabelRender
import com.example.explorelens.ml.render.PointCloudRender
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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
            takeSnapshot(frame, session)
        }
    }

    private fun takeSnapshot(frame: Frame, session: Session) {
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
    }

    fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }
}