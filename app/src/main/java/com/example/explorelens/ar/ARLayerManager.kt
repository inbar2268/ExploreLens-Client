package com.example.explorelens.ar

import android.content.Context
import android.util.Log
import com.example.explorelens.ar.render.LayerLabelRenderer
import com.example.explorelens.common.samplerender.SampleRender
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class for managing AR layer labels for nearby places
 */
class ARLayerManager(private val context: Context) {
    companion object {
        private const val TAG = "ARLayerManager"
        private const val MINIMUM_DISTANCE = 10.0 // Minimum distance in meters
    }

    // Renderer for layer labels
    private val layerLabelRenderer = LayerLabelRenderer()

    // List of layer labels with their information
    private val layerLabels = CopyOnWriteArrayList<LayerLabelInfo>()

    // Data class to hold layer label information
    data class LayerLabelInfo(
        val anchor: com.google.ar.core.Anchor,
        val placeInfo: Map<String, Any>
    )

    /**
     * Initialize the layer label renderer
     */
    fun onSurfaceCreated(render: SampleRender) {
        Log.d(TAG, "Initializing ARLayerManager")
        layerLabelRenderer.onSurfaceCreated(render, context)
    }

    /**
     * Add a new layer label using an existing anchor
     */
    fun addLayerLabel(
        session: com.google.ar.core.Session,
        anchor: com.google.ar.core.Anchor,
        placeInfo: Map<String, Any>
    ): LayerLabelInfo {
        Log.d(TAG, "Adding layer label for: ${placeInfo["name"]} using existing anchor")

        // Create and add the layer label info
        val layerLabel = LayerLabelInfo(anchor, placeInfo)
        layerLabels.add(layerLabel)

        return layerLabel
    }

    /**
     * Add a new layer label at the specified world position
     */
    fun addLayerLabel(
        session: com.google.ar.core.Session,
        worldPosition: FloatArray,
        placeInfo: Map<String, Any>
    ): LayerLabelInfo? {
        try {
            Log.d(TAG, "Adding layer label for: ${placeInfo["name"]} at position")

            // Create pose at the specified world position
            val pose = Pose.makeTranslation(
                worldPosition[0],
                worldPosition[1],
                worldPosition[2]
            )

            // Create an anchor at this position
            val anchor = session.createAnchor(pose)

            // Create and add the layer label info
            val layerLabel = LayerLabelInfo(anchor, placeInfo)
            layerLabels.add(layerLabel)

            return layerLabel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add layer label", e)
            return null
        }
    }

    /**
     * Remove a layer label
     */
    fun removeLayerLabel(label: LayerLabelInfo) {
        layerLabels.remove(label)
        label.anchor.detach()
    }

    /**
     * Clear all layer labels
     */
    fun clearLayerLabels() {
        layerLabels.forEach { it.anchor.detach() }
        layerLabels.clear()
    }

    /**
     * Draw all layer labels
     */
    fun drawLayerLabels(
        render: SampleRender,
        viewProjectionMatrix: FloatArray,
        cameraPose: Pose,
        frame: Frame
    ) {
        // Draw each layer label
        for (label in layerLabels) {
            val anchor = label.anchor

            // Only draw labels with tracking anchors
            if (anchor.trackingState != TrackingState.TRACKING) continue

            // Draw the layer label
            layerLabelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchor.pose,
                cameraPose,
                label.placeInfo
            )
        }
    }

    /**
     * Process a place list and create layer labels at appropriate positions
     */
    fun processPlaces(
        session: com.google.ar.core.Session,
        places: List<Map<String, Any>>,
        currentPose: Pose
    ) {
        Log.d(TAG, "Processing ${places.size} places for layer labels")

        // Clear existing layer labels
        clearLayerLabels()

        // Process each place
        for (place in places) {
            try {
                // Extract location from place info
                @Suppress("UNCHECKED_CAST")
                val location = place["location"] as? Map<String, Double>

                if (location != null) {
                    val lat = location["lat"] ?: continue
                    val lng = location["lng"] ?: continue

                    // Convert lat/lng to a position relative to current pose
                    // This is a simplified example - in a real app, you'd need a proper
                    // geographic/geodetic to local coordinate system conversion
                    val relativePosition = computeRelativePosition(
                        currentPose,
                        lat,
                        lng
                    )

                    // Create a layer label at this position
                    addLayerLabel(session, relativePosition, place)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing place: ${place["name"]}", e)
            }
        }
    }

    /**
     * Handle a touch event - determine if a layer label was touched
     */
    fun handleTouch(
        frame: Frame,
        cameraPose: Pose,
        touchX: Float,
        touchY: Float
    ): LayerLabelInfo? {
        // This is a simplified hit test for layer labels
        // A complete implementation would raycast from the touch point

        // Perform a hit test to get a ray
        val hitResults = frame.hitTest(touchX, touchY)
        if (hitResults.isEmpty()) return null

        // Get camera position
        val cameraPos = cameraPose.translation

        // Find the closest label along this ray
        var closestLabel: LayerLabelInfo? = null
        var closestDistance = Float.MAX_VALUE

        for (label in layerLabels) {
            if (label.anchor.trackingState != TrackingState.TRACKING) continue

            val anchorPos = label.anchor.pose.translation

            // Simple distance calculation (could be improved with proper ray-testing)
            val distance = calcDistance(cameraPos, anchorPos)

            // Check if this is closer than previous closest
            if (distance < closestDistance) {
                closestDistance = distance
                closestLabel = label
            }
        }

        return closestLabel
    }

    /**
     * Calculate distance between two points
     */
    private fun calcDistance(point1: FloatArray, point2: FloatArray): Float {
        val dx = point1[0] - point2[0]
        val dy = point1[1] - point2[1]
        val dz = point1[2] - point2[2]
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Compute relative position from latitude/longitude to local coordinates
     * This is a simplified conversion for demonstration purposes
     */
    private fun computeRelativePosition(
        currentPose: Pose,
        latitude: Double,
        longitude: Double
    ): FloatArray {
        // Earth's radius in meters
        val EARTH_RADIUS = 6378137.0

        // These values would come from device location sensor in a real app
        // For this example, we'll use simplified random positioning

        // Calculate relative position based on current pose
        // In a real app, this would use accurate geospatial calculations
        val distanceRange = 5.0 // 5-25 meters away
        val angleRadians = Math.random() * 2 * Math.PI

        // Calculate position relative to current pose
        val dx = (distanceRange * Math.cos(angleRadians)).toFloat()
        val dz = (distanceRange * Math.sin(angleRadians)).toFloat()

        // Set a fixed height
        val dy = 0f

        // Apply the offset to current position
        return floatArrayOf(
            currentPose.tx() + dx,
            currentPose.ty() + dy,
            currentPose.tz() + dz
        )
    }
}