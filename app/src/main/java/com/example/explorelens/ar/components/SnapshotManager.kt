package com.example.explorelens.ar.components

import android.content.Context
import android.util.Log
import com.example.explorelens.BuildConfig
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.common.helpers.DisplayRotationHelper
import com.example.explorelens.ar.classification.utils.ImageUtils
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.example.explorelens.data.model.siteDetectionData.SiteInformation
import com.example.explorelens.data.repository.DetectionResultRepository
import com.example.explorelens.extensions.convertYuv
import com.example.explorelens.extensions.toFile
import com.example.explorelens.model.Snapshot
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class SnapshotManager(
    private val context: Context,
    private val view: ArActivityView,
    private val displayRotationHelper: DisplayRotationHelper,
    private val networkScope: CoroutineScope,
    private val backgroundScope: CoroutineScope
) {
    companion object {
        private const val TAG = "SnapshotManager"
        private const val NETWORK_TIMEOUT_MS = 30000L
        private const val MOCK_DELAY_MS = 2000L
    }

    interface SnapshotCallback {
        fun onSnapshotResult(result: ImageAnalyzedResult?)
        fun onSnapshotError(message: String)
        fun showSnackbar(message: String)
    }

    private var callback: SnapshotCallback? = null

    fun setCallback(callback: SnapshotCallback) {
        this.callback = callback
    }

    fun takeSnapshot(frame: Frame, session: Session): Snapshot {
        val camera = frame.camera
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)

        var path: String? = null

        backgroundScope.launch {
            try {
                frame.tryAcquireCameraImage()?.use { cameraImage ->
                    val cameraId = session.cameraConfig.cameraId
                    val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
                    val convertYuv = convertYuv(context, cameraImage)
                    val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)
                    convertYuv.recycle()

                    val file = withContext(Dispatchers.IO) {
                        rotatedImage.toFile(context, "snapshot")
                    }
                    rotatedImage.recycle()
                    path = file.absolutePath

                    processImageCapture(path)
                } ?: run {
                    Log.e(TAG, "Camera image is null")
                    withContext(Dispatchers.Main) {
                        view.setScanningActive(false)
                        callback?.onSnapshotError("Camera image not available. Please try again.")
                    }
                }
            } catch (e: NotYetAvailableException) {
                Log.e(TAG, "No image available yet")
                withContext(Dispatchers.Main) {
                    view.setScanningActive(false)
                    callback?.onSnapshotError("Camera image not available. Please try again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing image", e)
                withContext(Dispatchers.Main) {
                    view.setScanningActive(false)
                    callback?.onSnapshotError("Failed to capture image: ${e.message}")
                }
            }
        }

        return Snapshot(
            timestamp = frame.timestamp,
            cameraPose = camera.pose,
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix
        )
    }

    private fun processImageCapture(imagePath: String?) {
        if (BuildConfig.USE_MOCK_DATA) {
            processMockData(imagePath)
        } else {
            processRealImageAnalysis(imagePath)
        }
    }

    private fun processMockData(imagePath: String?) {
        imagePath?.let {
            Log.d(TAG, "Using mock data for path: $it")
        }

        val mockResults = ImageAnalyzedResult(
            status = "assume",
            description = "Famous site detected in full image.",
            siteInformation = SiteInformation(
                label = "full-image",
                x = 0.5f,
                y = 0.5f,
                siteName = "Taj Mahal"
                        //siteName = "Colosseum"
            ),
            //siteInfoId = "6818fd47b249f52360e546ec",
            siteInfoId = "6850169248601af1be7c8fe3",
        )

        networkScope.launch {
            delay(MOCK_DELAY_MS)
            withContext(Dispatchers.Main) {
                callback?.onSnapshotResult(mockResults)
                view.setScanningActive(false)
            }
        }
    }

    private fun processRealImageAnalysis(imagePath: String?) {
        // Set timeout
        networkScope.launch {
            delay(NETWORK_TIMEOUT_MS)
            withContext(Dispatchers.Main) {
                if (view.binding.cameraButtonContainer.isEnabled) {
                    view.setScanningActive(false)
                    callback?.onSnapshotError("Request timed out. Please try again.")
                }
            }
        }

        // Start actual analysis
        networkScope.launch {
            try {
                imagePath?.let {
                    Log.d(TAG, "Starting network request with path: $it")
                    analyzeImage(it)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        view.setScanningActive(false)
                        callback?.onSnapshotError("Failed to capture image. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network request failed", e)
                withContext(Dispatchers.Main) {
                    view.setScanningActive(false)
                    callback?.onSnapshotError("Network error: ${e.message}")
                }
            }
        }
    }

    private suspend fun analyzeImage(path: String) {
        Log.d(TAG, "Starting image analysis for path: $path")

        try {
            val repository = DetectionResultRepository()
            val result = repository.getAnalyzedResult(path)

            withContext(Dispatchers.Main) {
                view.setScanningActive(false)

                result.onSuccess { analyzedResult ->
                    Log.d(TAG, "Analysis successful: ${analyzedResult.status}")
                    if (analyzedResult.status == "failure") {
                        Log.d(TAG, "No objects detected: ${analyzedResult.description}")
                        callback?.showSnackbar("No objects detected in the image")
                        callback?.onSnapshotResult(null)
                    } else {
                        Log.d(TAG, "Site detected: ${analyzedResult.siteInformation?.siteName ?: "Unknown"}")
                        callback?.onSnapshotResult(analyzedResult)
                    }
                }

                result.onFailure { error ->
                    Log.e(TAG, "Analysis failed: ${error.localizedMessage}")
                    callback?.onSnapshotError("Error analyzing the image: ${error.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in analyzeImage", e)
            withContext(Dispatchers.Main) {
                view.setScanningActive(false)
                callback?.onSnapshotError("Network request failed: ${e.message}")
            }
        }
    }

    private fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }
}