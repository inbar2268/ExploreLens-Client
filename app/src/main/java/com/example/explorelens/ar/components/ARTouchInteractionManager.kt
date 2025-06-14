package com.example.explorelens.ar.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.explorelens.R
import com.example.explorelens.ArActivity
import com.example.explorelens.ar.ARLayerManager
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.model.ARLabeledAnchor
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.sqrt

public class ARTouchInteractionManager(
    private val activity: ArActivity,
    private val view: ArActivityView,
    private val anchorManager: AnchorManager
) {
    companion object {
        private const val TAG = "ARTouchInteractionManager"

        private const val LABEL_SCREEN_SIZE = 0.15f
        private const val LABEL_ASPECT_RATIO = 1.0f / 0.6f
        private const val LAYER_LABEL_SCREEN_SIZE = 0.15f
        private const val LAYER_LABEL_ASPECT_RATIO = 1.0f / 0.6f
    }

    private var pendingTouchX: Float? = null
    private var pendingTouchY: Float? = null
    var scanButtonWasPressed = false
        private set

    // Cache screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    // ARLayerManager instance, now nullable and set via a setter
    private var layerManager: ARLayerManager? = null


    interface TouchInteractionListener {
        fun onScanButtonPressed()
        fun onAnchorClicked(anchor: ARLabeledAnchor)
        fun onLayerLabelClicked(layerLabel: ARLayerManager.LayerLabelInfo) // Changed type to LayerLabelInfo
        fun onLayerLabelClosed(layerLabel: ARLayerManager.LayerLabelInfo) // New callback for X button
    }

    private var listener: TouchInteractionListener? = null

    fun setTouchInteractionListener(listener: TouchInteractionListener) {
        this.listener = listener
    }

    fun setLayerManager(layerManager: ARLayerManager) {
        this.layerManager = layerManager
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
                    // Notify listener that scan button was pressed (after animation for UP)
                    if (event.action == MotionEvent.ACTION_UP) {
                        listener?.onScanButtonPressed()
                    }
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
                // Clear pending touch after processing
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
            Log.w(TAG, "Camera is not tracking. Cannot process touch.")
            return
        }

        if (screenWidth == 0 || screenHeight == 0) {
            Log.w(TAG, "Screen dimensions not set. Cannot process touch.")
            return
        }

        try {
            Log.d(TAG, "Processing touch at ($x, $y) on screen ${screenWidth}x${screenHeight}")

            val closestAnchor = findClosestAnchorToTouch(x, y, frame, camera)
            if (closestAnchor != null) {
                Log.d(TAG, "Anchor '${closestAnchor.label}' clicked.")
                handleAnchorClick(closestAnchor)
                return
            }

            val layerLabelWithXButton = findLayerLabelXButtonTouch(x, y, frame, camera)
            if (layerLabelWithXButton != null) {
                Log.d(TAG, "Layer Label X button '${layerLabelWithXButton.placeInfo["name"]}' clicked.")
                handleLayerLabelXButtonClick(layerLabelWithXButton)
                return
            }

            val closestLayerLabel = findClosestLayerLabelToTouch(x, y, frame, camera)
            if (closestLayerLabel != null) {
                Log.d(TAG, "Layer Label '${closestLayerLabel.placeInfo["name"]}' clicked.")
                handleLayerLabelClick(closestLayerLabel)
                return
            }

            Log.d(TAG, "No AR object clicked at ($x, $y).")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing touch event", e)
        }
    }
    private fun findLayerLabelXButtonTouch(
        touchX: Float,
        touchY: Float,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): ARLayerManager.LayerLabelInfo? {
        val currentLayerManager = this.layerManager
            ?: run {
                Log.w(TAG, "ARLayerManager is not set, cannot process layer label X button touch.")
                return null
            }

        var closestLayerLabel: ARLayerManager.LayerLabelInfo? = null
        var closestDistance = Float.MAX_VALUE

        val layerLabelsToCheck = currentLayerManager.getCurrentlyVisibleLabels()

        Log.d(TAG, "Checking ${layerLabelsToCheck.size} layer labels for X button touch at ($touchX, $touchY)")

        for (layerLabel in layerLabelsToCheck) {
            if (layerLabel.anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            val xButtonBounds = calculateLayerLabelXButtonBounds(layerLabel, frame, camera)

            if (xButtonBounds != null) {
                val labelName = layerLabel.placeInfo["name"] ?: "Unknown"

                // Check if touch is within X button bounds
                val padding = 20f
                if (touchX >= (xButtonBounds.left - padding) &&
                    touchX <= (xButtonBounds.right + padding) &&
                    touchY >= (xButtonBounds.top - padding) &&
                    touchY <= (xButtonBounds.bottom + padding)) {

                    // Calculate 3D distance from camera to layer label's position
                    val labelPos = layerLabel.anchor.pose.translation
                    val cameraPos = camera.pose.translation

                    val dx = labelPos[0] - cameraPos[0]
                    val dy = labelPos[1] - cameraPos[1]
                    val dz = labelPos[2] - cameraPos[2]
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)

                    Log.d(TAG, "Touch hit layer label X button '$labelName' at distance $distance meters.")

                    // If this label is closer than previously hit labels, select it
                    if (distance < closestDistance) {
                        closestLayerLabel = layerLabel
                        closestDistance = distance
                    }
                }
            }
        }
        return closestLayerLabel
    }

    private fun calculateLayerLabelXButtonBounds(
        layerLabel: ARLayerManager.LayerLabelInfo,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): LabelBounds? {
        try {
            // First get the label's overall screen bounds
            val labelPose = layerLabel.anchor.pose
            val worldPos = floatArrayOf(
                labelPose.tx(),
                labelPose.ty() + 0.1f, // Same offset as in LayerLabelRenderer
                labelPose.tz(),
                1.0f
            )

            val labelScreenBounds = calculateScreenBounds(worldPos, frame, camera, LAYER_LABEL_SCREEN_SIZE, LAYER_LABEL_ASPECT_RATIO)
                ?: return null

            // Get X button bounds from texture cache (normalized coordinates 0-1)
            val currentLayerManager = this.layerManager ?: return null
            val textureCache = currentLayerManager.getTextureCache() // You'll need to expose this method
            val xButtonNormalizedBounds = textureCache.getXButtonBounds()

            // Convert normalized coordinates to screen pixel coordinates
            val labelWidth = labelScreenBounds.right - labelScreenBounds.left
            val labelHeight = labelScreenBounds.bottom - labelScreenBounds.top

            val xButtonLeft = labelScreenBounds.left + (xButtonNormalizedBounds.left * labelWidth)
            val xButtonTop = labelScreenBounds.top + (xButtonNormalizedBounds.top * labelHeight)
            val xButtonRight = labelScreenBounds.left + (xButtonNormalizedBounds.right * labelWidth)
            val xButtonBottom = labelScreenBounds.top + (xButtonNormalizedBounds.bottom * labelHeight)

            return LabelBounds(
                left = xButtonLeft,
                top = xButtonTop,
                right = xButtonRight,
                bottom = xButtonBottom
            )

        } catch (e: Exception) {
            val labelName = layerLabel.placeInfo["name"] ?: "Unknown"
            Log.e(TAG, "Error calculating X button bounds for '$labelName'", e)
            return null
        }
    }


    private fun findClosestAnchorToTouch(
        touchX: Float,
        touchY: Float,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): ARLabeledAnchor? {
        var closestAnchor: ARLabeledAnchor? = null
        var closestDistance = Float.MAX_VALUE // Distance to camera

        val anchorsToCheck = anchorManager.getAnchorsForRendering()

        Log.d(TAG, "Checking ${anchorsToCheck.size} anchors for touch at ($touchX, $touchY)")

        for (anchor in anchorsToCheck) {
            // Only consider actively tracking anchors
            if (anchor.anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            val labelBounds = calculateLabelScreenBounds(anchor, frame, camera)

            if (labelBounds != null) {

                if (touchX >= labelBounds.left && touchX <= labelBounds.right &&
                    touchY >= labelBounds.top && touchY <= labelBounds.bottom) {

                    // Calculate 3D distance from camera to anchor's real position
                    val anchorPos = anchor.anchor.pose.translation
                    val cameraPos = camera.pose.translation

                    val dx = anchorPos[0] - cameraPos[0]
                    val dy = anchorPos[1] - cameraPos[1]
                    val dz = anchorPos[2] - cameraPos[2]
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)

                    Log.d(TAG, "Touch hit anchor '${anchor.label}' at distance $distance meters.")

                    // If this anchor is closer than previously hit anchors, select it
                    if (distance < closestDistance) {
                        closestAnchor = anchor
                        closestDistance = distance
                    }
                }
            }
        }
        return closestAnchor
    }

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
            // Re-using the common projection logic
            return calculateScreenBounds(worldPos, frame, camera, LABEL_SCREEN_SIZE, LABEL_ASPECT_RATIO)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating label bounds for '${anchor.label}'", e)
            return null
        }
    }

    private fun findClosestLayerLabelToTouch(
        touchX: Float,
        touchY: Float,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): ARLayerManager.LayerLabelInfo? {
        // Ensure layerManager is initialized
        val currentLayerManager = this.layerManager
            ?: run {
                Log.w(TAG, "ARLayerManager is not set, cannot process layer label touch.")
                return null
            }

        var closestLayerLabel: ARLayerManager.LayerLabelInfo? = null
        var closestDistance = Float.MAX_VALUE // Distance to camera

        val layerLabelsToCheck = currentLayerManager.getCurrentlyVisibleLabels()

        Log.d(TAG, "Checking ${layerLabelsToCheck.size} layer labels for touch at ($touchX, $touchY)")

        for (layerLabel in layerLabelsToCheck) {
            // Only consider actively tracking anchors for layer labels
            if (layerLabel.anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            val labelBounds = calculateLayerLabelScreenBounds(layerLabel, frame, camera)

            if (labelBounds != null) {
                val labelName = layerLabel.placeInfo["name"] ?: "Unknown"

                // Check if touch is strictly inside the label bounds
                if (touchX >= labelBounds.left && touchX <= labelBounds.right &&
                    touchY >= labelBounds.top && touchY <= labelBounds.bottom) {

                    // Calculate 3D distance from camera to layer label's real position
                    val labelPos = layerLabel.anchor.pose.translation
                    val cameraPos = camera.pose.translation

                    val dx = labelPos[0] - cameraPos[0]
                    val dy = labelPos[1] - cameraPos[1]
                    val dz = labelPos[2] - cameraPos[2]
                    val distance = sqrt(dx * dx + dy * dy + dz * dz)

                    Log.d(TAG, "Touch hit layer label '$labelName' at distance $distance meters.")

                    // If this label is closer than previously hit labels, select it
                    if (distance < closestDistance) {
                        closestLayerLabel = layerLabel
                        closestDistance = distance
                    }
                }
            }
        }
        return closestLayerLabel
    }

    private fun calculateLayerLabelScreenBounds(
        layerLabel: ARLayerManager.LayerLabelInfo,
        frame: Frame,
        camera: com.google.ar.core.Camera
    ): LabelBounds? {
        try {
            // Get the layer label position (with Y offset matching LayerLabelRenderer)
            val labelPose = layerLabel.anchor.pose
            val worldPos = floatArrayOf(
                labelPose.tx(),
                labelPose.ty() + 0.1f, // Same offset as in LayerLabelRenderer
                labelPose.tz(),
                1.0f
            )
            return calculateScreenBounds(worldPos, frame, camera, LAYER_LABEL_SCREEN_SIZE, LAYER_LABEL_ASPECT_RATIO)

        } catch (e: Exception) {
            val labelName = layerLabel.placeInfo["name"] ?: "Unknown"
            Log.e(TAG, "Error calculating layer label bounds for '$labelName'", e)
            return null
        }
    }
    private fun calculateScreenBounds(
        worldPos: FloatArray,
        frame: Frame, // Frame is not directly used here but could be if you needed other frame data
        camera: com.google.ar.core.Camera,
        screenSize: Float, // Normalized screen size (e.g., 0.15 for 15% of screen height)
        aspectRatio: Float // Width / Height
    ): LabelBounds? {
        try {
            // Project world position to screen coordinates
            val projectionMatrix = FloatArray(16)
            val viewMatrix = FloatArray(16)
            val viewProjectionMatrix = FloatArray(16)

            // Get camera matrices. Near and far planes should match ARCore's rendering.
            camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
            camera.getViewMatrix(viewMatrix, 0)

            // Combine projection and view matrices
            android.opengl.Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

            // Transform world position to clip space (4D homogeneous coordinates)
            val clipSpacePos = FloatArray(4)
            android.opengl.Matrix.multiplyMV(clipSpacePos, 0, viewProjectionMatrix, 0, worldPos, 0)

            // Check if point is behind camera (w-component <= 0)
            if (clipSpacePos[3] <= 0) {
                // Log.d(TAG, "Position is behind camera, clipW=${clipSpacePos[3]}") // Too verbose for every check
                return null
            }

            // Convert to normalized device coordinates (NDC) by dividing by w
            val ndcX = clipSpacePos[0] / clipSpacePos[3]
            val ndcY = clipSpacePos[1] / clipSpacePos[3]
            // ndcZ = clipSpacePos[2] / clipSpacePos[3] // Not strictly needed for 2D bounds check

            // Check if point is outside viewing frustum in NDC (excluding Z for 2D bounds)
            if (abs(ndcX) > 1.0f || abs(ndcY) > 1.0f) {
                // Log.d(TAG, "Position is outside viewing frustum: NDC($ndcX, $ndcY)") // Too verbose for every check
                return null
            }

            // Convert NDC to screen coordinates (pixels)
            // ARCore NDC range is [-1, 1]. Screen coordinates typically [0, screenWidth] and [0, screenHeight].
            val screenX = (ndcX + 1.0f) * 0.5f * screenWidth
            val screenY = (1.0f - ndcY) * 0.5f * screenHeight // Flip Y for typical Android screen coordinates (top-left is 0,0)

            // Calculate label dimensions in screen space based on `screenSize` (as percentage of screen height)
            val labelHeightPixels = screenSize * screenHeight
            val labelWidthPixels = labelHeightPixels * aspectRatio

            // Calculate the rectangular bounds around the projected screen point
            val halfWidth = labelWidthPixels * 0.5f
            val halfHeight = labelHeightPixels * 0.5f

            val bounds = LabelBounds(
                left = screenX - halfWidth,
                top = screenY - halfHeight,
                right = screenX + halfWidth,
                bottom = screenY + halfHeight
            )

            // Log.d(TAG, "World(${worldPos[0]}, ${worldPos[1]}, ${worldPos[2]}) -> Screen(${screenX}, ${screenY}) -> Bounds(${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom})") // Too verbose
            return bounds

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating screen bounds in calculateScreenBounds", e)
            return null
        }
    }


    private fun handleAnchorClick(clickedAnchor: ARLabeledAnchor) {
        Log.d(TAG, "handleAnchorClick called for anchor: ${clickedAnchor.label}")

        val siteId = clickedAnchor.siteId
        val siteName = clickedAnchor.siteName
        Log.d(TAG, "Clicked on anchor: $siteId")

        activity.runOnUiThread {
            view.snackbarHelper.hide(activity) // Hide any current snackbar
            activity.findViewById<View>(R.id.cameraButtonContainer)?.visibility = View.GONE // Hide camera button
            // Show site details using ArActivityView
            clickedAnchor.siteId?.let { siteId ->
                view.showSiteDetails(siteId, clickedAnchor.fullDescription, clickedAnchor.siteName)
            }
            listener?.onAnchorClicked(clickedAnchor) // Notify external listener
        }
    }

    private fun handleLayerLabelClick(layerLabel: ARLayerManager.LayerLabelInfo?) {
        val placeName = layerLabel?.placeInfo?.get("name") as? String ?: "Unknown Place"
        Log.d(TAG, "handleLayerLabelClick called for place: $placeName")

        activity.runOnUiThread {
            view.snackbarHelper.showMessage(activity, "Tapped on: $placeName") // Show a simple message
            layerLabel?.let {
                listener?.onLayerLabelClicked(it) // Notify external listener, passing the full LayerLabelInfo
            }
        }
    }

    private fun handleLayerLabelXButtonClick(layerLabel: ARLayerManager.LayerLabelInfo) {
        val placeName = layerLabel.placeInfo["name"] as? String ?: "Unknown Place"
        Log.d(TAG, "handleLayerLabelXButtonClick called for place: $placeName")

        activity.runOnUiThread {
            layerManager?.removeLabel(layerLabel)
            view.snackbarHelper.showMessage(activity, "Closed: $placeName")
            listener?.onLayerLabelClosed(layerLabel)
        }
    }

    private data class LabelBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}