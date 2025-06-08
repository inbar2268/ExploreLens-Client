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
import com.example.explorelens.ar.components.AnchorManager.Companion
import com.example.explorelens.model.ARLabeledAnchor
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

class ARTouchInteractionManager(
    private val activity: ArActivity,
    private val view: ArActivityView,
    private val anchorManager: AnchorManager
) {
    companion object {
        private const val TAG = "ARTouchInteractionManager"
    }

    private var pendingTouchX: Float? = null
    private var pendingTouchY: Float? = null
    var scanButtonWasPressed = false
        private set

    interface TouchInteractionListener {
        fun onScanButtonPressed()
        fun onAnchorClicked(anchor: ARLabeledAnchor)
    }

    private var listener: TouchInteractionListener? = null

    fun setTouchInteractionListener(listener: TouchInteractionListener) {
        this.listener = listener
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

        try {
            val cameraPose = camera.pose
            val cameraPos = cameraPose.translation
            val forward = cameraPose.zAxis

            // Normalize forward direction (pointing out of the camera)
            val forwardNorm = floatArrayOf(-forward[0], -forward[1], -forward[2])

            Log.d(TAG, "Camera position: ${cameraPos.contentToString()}")
            Log.d(TAG, "Touch coordinates: $x, $y")

            // Create a hit ray from the touch coordinates
            val hitResults = frame.hitTest(x, y)

            // Calculate ray direction from touch
            val ray = if (hitResults.isNotEmpty()) {
                val hitPos = hitResults.first().hitPose.translation
                val rayX = hitPos[0] - cameraPos[0]
                val rayY = hitPos[1] - cameraPos[1]
                val rayZ = hitPos[2] - cameraPos[2]

                val rayLength = sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ)
                if (rayLength > 0) {
                    floatArrayOf(rayX / rayLength, rayY / rayLength, rayZ / rayLength)
                } else {
                    forwardNorm
                }
            } else {
                forwardNorm
            }

            Log.d(TAG, "Ray direction: ${ray.contentToString()}")

            val closestAnchor = findClosestAnchorToTouch(x, y, frame, cameraPos, ray)

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

    fun findClosestAnchorToTouch(
        touchX: Float,
        touchY: Float,
        frame: Frame,
        cameraPos: FloatArray,
        ray: FloatArray
    ): ARLabeledAnchor? {
        var closestAnchor: ARLabeledAnchor? = null
        var closestDistance = Float.MAX_VALUE

        val anchorsToCheck = anchorManager.getAnchorsForRendering()
        val touchThreshold = 0.2f

        for (anchor in anchorsToCheck) {
            val anchorPose = anchor.anchor.pose
            val anchorPos = anchorPose.translation

            // Vector from camera to anchor
            val toAnchorX = anchorPos[0] - cameraPos[0]
            val toAnchorY = anchorPos[1] - cameraPos[1]
            val toAnchorZ = anchorPos[2] - cameraPos[2]

            val distanceToAnchor = sqrt(toAnchorX * toAnchorX + toAnchorY * toAnchorY + toAnchorZ * toAnchorZ)

            // Calculate dot product to determine if anchor is in front of camera
            val dotProduct = toAnchorX * ray[0] + toAnchorY * ray[1] + toAnchorZ * ray[2]

            // Distance from ray to anchor (using vector rejection formula)
            val projection = dotProduct / distanceToAnchor
            val projectionDistance = distanceToAnchor * projection

            // Calculate the closest point on the ray
            val closestPointX = cameraPos[0] + ray[0] * projectionDistance
            val closestPointY = cameraPos[1] + ray[1] * projectionDistance
            val closestPointZ = cameraPos[2] + ray[2] * projectionDistance

            // Distance from this point to the anchor
            val dX = closestPointX - anchorPos[0]
            val dY = closestPointY - anchorPos[1]
            val dZ = closestPointZ - anchorPos[2]
            val perpendicularDistance = sqrt(dX * dX + dY * dY + dZ * dZ)

            if (dotProduct > 0 && perpendicularDistance < touchThreshold) {
                if (distanceToAnchor < closestDistance) {
                    closestAnchor = anchor
                    closestDistance = distanceToAnchor
                }
            }

            Log.d(TAG, "Anchor ${anchor.label}: distance=$distanceToAnchor, perpendicular=$perpendicularDistance, dot=$dotProduct")
        }

        return closestAnchor
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
}