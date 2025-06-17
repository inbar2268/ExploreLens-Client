package com.example.explorelens.ar

import android.content.Context
import android.util.Log
import com.example.explorelens.ar.render.LayerLabelRenderer
import com.example.explorelens.ar.render.LayerLabelTextureCache
import com.example.explorelens.common.samplerender.SampleRender
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Anchor
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.CopyOnWriteArrayList

class ARLayerManager(private val context: Context) {
    companion object {
        private const val TAG = "ARLayerManager"
        private const val MINIMUM_DISTANCE = 10.0 // Minimum distance in meters
    }

    private val layerLabelRenderer = LayerLabelRenderer()
    private val layerLabels = CopyOnWriteArrayList<LayerLabelInfo>()
    private val textureCache = LayerLabelTextureCache(context)
    private val currentlyVisibleLabels = CopyOnWriteArrayList<LayerLabelInfo>()

    data class LayerLabelInfo(
        val anchor: Anchor,
        val placeInfo: Map<String, Any?>
    )

    fun onSurfaceCreated(render: SampleRender) {
        Log.d(TAG, "Initializing ARLayerManager")
        layerLabelRenderer.onSurfaceCreated(render, context)
    }

    fun addLayerLabel(
        anchor: Anchor,
        placeInfo: Map<String, Any?>
    ): LayerLabelInfo {
        Log.d(TAG, "Adding layer label for: ${placeInfo["name"]} using existing anchor")
        val layerLabel = LayerLabelInfo(anchor, placeInfo)
        layerLabels.add(layerLabel)
        return layerLabel
    }

    fun addLayerLabel(
        session: Session,
        worldPosition: FloatArray,
        placeInfo: Map<String, Any>
    ): LayerLabelInfo? {
        return try {
            Log.d(TAG, "Adding layer label for: ${placeInfo["name"]} at position")
            val pose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])
            val anchor = session.createAnchor(pose)
            val layerLabel = LayerLabelInfo(anchor, placeInfo)
            layerLabels.add(layerLabel)
            layerLabel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add layer label", e)
            null
        }
    }

    fun removeLayerLabel(label: LayerLabelInfo) {
        layerLabels.remove(label)
        label.anchor.detach()
    }

    fun clearLayerLabels() {
        layerLabels.forEach { it.anchor.detach() }
        layerLabels.clear()
    }

    fun drawLayerLabels(
        render: SampleRender,
        viewProjectionMatrix: FloatArray,
        cameraPose: Pose,
        frame: Frame
    ) {
        val maxDistanceMeters = 500f
        val fovDegrees = 50f

        currentlyVisibleLabels.clear()

        for (label in layerLabels) {
            val anchor = label.anchor
            val pose = anchor.pose


            if (anchor.trackingState != TrackingState.TRACKING) continue

            val dx = pose.tx() - cameraPose.tx()
            val dy = pose.ty() - cameraPose.ty()
            val dz = pose.tz() - cameraPose.tz()
            val distance = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())

            if (distance > maxDistanceMeters) {
                //Log.d("AR-Debug", "⛔️ too far, skipping")
                continue
            }

            if (!isInFront(cameraPose, pose)) {
                //Log.d("AR-Debug", "⛔️ behind, skipping")
                continue
            }

            if (!isWithinFOV(cameraPose, pose, fovDegrees)) {
                //Log.d("AR-Debug", "⛔️ out of FOV, skipping")
                continue
            }

            currentlyVisibleLabels.add(label)

            layerLabelRenderer.draw(
                render,
                viewProjectionMatrix,
                pose,
                cameraPose,
                label.placeInfo
            )
        }
    }


    fun isInFront(cameraPose: Pose, labelPose: Pose): Boolean {
        val cameraForward = floatArrayOf(
            -cameraPose.zAxis[0],
            -cameraPose.zAxis[1],
            -cameraPose.zAxis[2]
        )
        val directionToLabel = floatArrayOf(
            labelPose.tx() - cameraPose.tx(),
            labelPose.ty() - cameraPose.ty(),
            labelPose.tz() - cameraPose.tz()
        )
        val dotProduct =
            cameraForward[0] * directionToLabel[0] +
                    cameraForward[1] * directionToLabel[1] +
                    cameraForward[2] * directionToLabel[2]
        return dotProduct > 0
    }

    fun isWithinFOV(cameraPose: Pose, labelPose: Pose, fovDegrees: Float): Boolean {
        val cameraForward = floatArrayOf(
            -cameraPose.zAxis[0],
            -cameraPose.zAxis[1],
            -cameraPose.zAxis[2]
        )
        val directionToLabel = floatArrayOf(
            labelPose.tx() - cameraPose.tx(),
            labelPose.ty() - cameraPose.ty(),
            labelPose.tz() - cameraPose.tz()
        )

        val dotProduct =
            cameraForward[0] * directionToLabel[0] +
                    cameraForward[1] * directionToLabel[1] +
                    cameraForward[2] * directionToLabel[2]

        val normCamera = Math.sqrt(
            (cameraForward[0] * cameraForward[0] +
                    cameraForward[1] * cameraForward[1] +
                    cameraForward[2] * cameraForward[2]).toDouble()
        )

        val normLabel = Math.sqrt(
            (directionToLabel[0] * directionToLabel[0] +
                    directionToLabel[1] * directionToLabel[1] +
                    directionToLabel[2] * directionToLabel[2]).toDouble()
        )

        val cosAngle = dotProduct / (normCamera * normLabel)
        val angleDegrees = Math.toDegrees(Math.acos(cosAngle)).toFloat()

        return angleDegrees < (fovDegrees / 2f)
    }


    fun getExistingPlaceIds(): Set<Any> {
        return layerLabels.mapNotNull { it.placeInfo["place_id"] }.toSet()
    }


    fun getAllLabels(): List<LayerLabelInfo> {
        return layerLabels.toList()
    }

    fun removeLabel(labelToRemove: LayerLabelInfo) {
        layerLabels.removeAll { it.anchor == labelToRemove.anchor }
        Log.d(TAG, "Removed layer label: ${labelToRemove.placeInfo["name"]}")
    }

    fun getTextureCache(): LayerLabelTextureCache {
        return textureCache
    }

    fun getCurrentlyVisibleLabels(): List<LayerLabelInfo> {
        return currentlyVisibleLabels.toList()
    }

}
