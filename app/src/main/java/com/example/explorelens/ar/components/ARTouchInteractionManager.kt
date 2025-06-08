package com.example.explorelens.ar.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.explorelens.R
import com.example.explorelens.ArActivity
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.model.ARLabeledAnchor
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.Coordinates2d
import kotlin.math.abs
import kotlin.math.sqrt

class ARTouchInteractionManager(
    private val activity: ArActivity,
    private val view: ArActivityView,
    private val anchorManager: AnchorManager
) {
    companion object {
        private const val TAG = "ARTouchInteractionManager"

        // Label dimensions from LabelRender - these should match exactly
        private const val LABEL_SCREEN_SIZE = 0.15f // This should match LabelRender.DEFAULT_SCREEN_SIZE
        private const val LABEL_ASPECT_RATIO = 1.0f / 0.6f // width / height from NDC_QUAD_COORDS_BUFFER
    }

    private var pendingTouchX: Float? = null
    private var pendingTouchY: Float? = null
    var scanButtonWasPressed = false
        private set

    // Cache screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    interface TouchInteractionListener {
        fun onScanButtonPressed()
        fun onAnchorClicked(anchor: ARLabeledAnchor)
    }

    private var listener: TouchInteractionListener? = null

    fun setTouchInteractionListener(listener: TouchInteractionListener) {
        this.listener = listener
    }

    fun updateScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        Log.d(TAG, "Screen dimensions updated: ${width}x${height}")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupCameraButton() {
        view.binding.cameraButtonContainer.setOnTouchListener { _, event ->
            if (!view.binding.cameraButtonContainer.isEnabled) {
                return@setOnTouchListener false
            }

            scanButtonWasPressed = true
            view.setScanningActive(true)

            view.binding.cameraButtonContainer.isEnabled = false

            Handler(Looper.getMainLooper()).postDelayed({
                view.binding.cameraButtonContainer.isEnabled = true
            }, 5000)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.binding.cameraInnerCircle.animate().scaleX(0.85f).scaleY(0.85f)
                        .setDuration(100).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.binding.cameraInnerCircle.animate().scaleX(1f).scaleY(1f).setDuration(100)
                        .start()
                }
            }
            false
        }
    }

    fun handleTouch(x: Float, y: Float) {
        Log.d(TAG, "Touch received at x=$x, y=$y")
        pendingTouchX = x
        pendingTouchY = y
    }

    fun processPendingTouch(frame: Frame, session: Session) {
        pendingTouchX?.let { x ->
            pendingTouchY?.let { y ->
                processTouchInGLThread(x, y, frame, session)
                pendingTouchX = null
                pendingTouchY = null
            }
        }
    }

    fun resetScanButton() {
        scanButtonWasPressed = false
    }

    private fun processTouchInGLThread(x: Float, y: Float, frame: Frame, session: Session) {
        val camera = frame.camera

        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera is not tracking.")
            return
        }

        if (screenWidth == 0 || screenHeight == 0) {
            Log.w(TAG, "Screen dimensions not set")
            return
        }

        try {
            Log.d(TAG, "Processing touch at ($x, $y) on screen ${screenWidth}x${screenHeight}")

            val closestAnchor = findClosestAnchorToTouchExact(x, y, frame, camera)

            if (closestAnchor != null) {
                Log.d(TAG, "Selected anchor: ${closestAnchor.label}")
                handleAnchorClick(closestAnchor)
            } else {
                Log.d(TAG, "No anchor selected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing touch", e)
        }
    }

    /**
     * Find the closest anchor to touch using exact screen space calculations
     */
    private fun findClosestAnchorToTouchExact(
        touchX: Float,
        touchY: Float,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): ARLabeledAnchor? {
        var closestAnchor: ARLabeledAnchor? = null
        var closestDistance = Float.MAX_VALUE

        val anchorsToCheck = anchorManager.getAnchorsForRendering()

        Log.d(TAG, "Checking ${anchorsToCheck.size} anchors for touch at ($touchX, $touchY)")

        for (anchor in anchorsToCheck) {
            if (anchor.anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            // Get the label bounds in screen coordinates
            val labelBounds = calculateLabelScreenBounds(anchor, frame, camera)

            if (labelBounds != null) {
                Log.d(TAG, "Anchor '${anchor.label}' bounds: left=${labelBounds.left}, top=${labelBounds.top}, right=${labelBounds.right}, bottom=${labelBounds.bottom}")

                // Check if touch is inside the label bounds
                if (touchX >= labelBounds.left && touchX <= labelBounds.right &&
                    touchY >= labelBounds.top && touchY <= labelBounds.bottom) {

                    // Calculate distance from camera to anchor for prioritization
                    val anchorPos = anchor.anchor.pose.translation
                    val cameraPos = camera.pose.translation

                    val dx = anchorPos[0] - cameraPos[0]
                    val dy = anchorPos[1] - cameraPos[1]
                    val dz = anchorPos[2] - cameraPos[2]
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)

                    Log.d(TAG, "Touch hit anchor '${anchor.label}' at distance $distance")

                    if (distance < closestDistance) {
                        closestAnchor = anchor
                        closestDistance = distance
                    }
                }
            }
        }

        return closestAnchor
    }

    /**
     * Calculate the exact screen bounds of a label
     */
    private fun calculateLabelScreenBounds(
        anchor: ARLabeledAnchor,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): LabelBounds? {
        try {
            // Get the anchor position (with Y offset matching LabelRender)
            val anchorPose = anchor.anchor.pose
            val worldPos = floatArrayOf(
                anchorPose.tx(),
                anchorPose.ty() + 0.1f, // Same offset as in LabelRender
                anchorPose.tz(),
                1.0f
            )

            // Project world position to screen coordinates
            val projectionMatrix = FloatArray(16)
            val viewMatrix = FloatArray(16)
            val viewProjectionMatrix = FloatArray(16)

            camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
            camera.getViewMatrix(viewMatrix, 0)

            // Multiply projection * view
            android.opengl.Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

            // Transform world position to clip space
            val clipSpacePos = FloatArray(4)
            android.opengl.Matrix.multiplyMV(clipSpacePos, 0, viewProjectionMatrix, 0, worldPos, 0)

            // Check if point is behind camera
            if (clipSpacePos[3] <= 0) {
                Log.d(TAG, "Anchor '${anchor.label}' is behind camera")
                return null
            }

            // Convert to normalized device coordinates (NDC)
            val ndcX = clipSpacePos[0] / clipSpacePos[3]
            val ndcY = clipSpacePos[1] / clipSpacePos[3]
            val ndcZ = clipSpacePos[2] / clipSpacePos[3]

            // Check if point is outside viewing frustum
            if (abs(ndcX) > 1.0f || abs(ndcY) > 1.0f || ndcZ < -1.0f || ndcZ > 1.0f) {
                Log.d(TAG, "Anchor '${anchor.label}' is outside viewing frustum: NDC($ndcX, $ndcY, $ndcZ)")
                return null
            }

            // Convert NDC to screen coordinates
            val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
            val screenY = (1.0f - ndcY) * 0.5f * screenHeight // Flip Y for screen coordinates

            // Calculate label dimensions in screen space
            // LABEL_SCREEN_SIZE is a proportion of screen height
            val labelHeightPixels = LABEL_SCREEN_SIZE * screenHeight
            val labelWidthPixels = labelHeightPixels * LABEL_ASPECT_RATIO

            // Calculate bounds
            val halfWidth = labelWidthPixels * 0.5f
            val halfHeight = labelHeightPixels * 0.5f

            val bounds = LabelBounds(
                left = screenX - halfWidth,
                top = screenY - halfHeight,
                right = screenX + halfWidth,
                bottom = screenY + halfHeight
            )

            Log.d(TAG, "Anchor '${anchor.label}' at world(${worldPos[0]}, ${worldPos[1]}, ${worldPos[2]}) -> NDC($ndcX, $ndcY) -> screen($screenX, $screenY) -> bounds(${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom})")

            return bounds

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating label bounds for '${anchor.label}'", e)
            return null
        }
    }

    private fun handleAnchorClick(clickedAnchor: ARLabeledAnchor) {
        Log.d(TAG, "handleAnchorClick called for anchor: ${clickedAnchor.label}")

        val siteId = clickedAnchor.siteId
        val siteName = clickedAnchor.siteName
        Log.d(TAG, "Clicked on anchor: $siteId")

        activity.runOnUiThread {
            view.snackbarHelper.hide(activity)
            activity.findViewById<View>(R.id.cameraButtonContainer)?.visibility = View.GONE
            if (siteId != null) {
                view.showSiteDetails(siteId, clickedAnchor.fullDescription, siteName)
            }
            listener?.onAnchorClicked(clickedAnchor)
        }
    }

    /**
     * Data class to represent label bounds in screen coordinates
     */
    private data class LabelBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}