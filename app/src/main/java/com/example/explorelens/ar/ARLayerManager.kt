package com.example.explorelens.ar

import android.content.Context
import android.util.Log
import com.example.explorelens.ar.render.LayerLabelRenderer
import com.example.explorelens.common.samplerender.SampleRender
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Anchor
import com.google.ar.core.Session
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Class for managing AR layer labels for nearby places
 */
class ARLayerManager(private val context: Context) {
    companion object {
        private const val TAG = "ARLayerManager"
        private const val MINIMUM_DISTANCE = 10.0 // Minimum distance in meters
    }

    private val layerLabelRenderer = LayerLabelRenderer()
    private val layerLabels = CopyOnWriteArrayList<LayerLabelInfo>()

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
        val MAX_RENDER_DISTANCE = 500f
        val MAX_LABELS = 20
        var drawnCount = 0

        for (label in layerLabels) {
            val anchor = label.anchor
            val anchorPose = anchor.pose

            val labelPosition = floatArrayOf(
                anchorPose.tx(),
                anchorPose.ty(),
                anchorPose.tz(),
                1f
            )
            val projected = FloatArray(4)
            android.opengl.Matrix.multiplyMV(projected, 0, viewProjectionMatrix, 0, labelPosition, 0)

            val isInFront = projected[2] < 0

            val ndcX = projected[0] / projected[3]
            val ndcY = projected[1] / projected[3]
            val isOnScreen = ndcX in -1f..1f && ndcY in -1f..1f


            val dx = anchorPose.tx() - cameraPose.tx()
            val dy = anchorPose.ty() - cameraPose.ty()
            val dz = anchorPose.tz() - cameraPose.tz()
            val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)


            if (!isInFront || !isOnScreen || distance > MAX_RENDER_DISTANCE) continue
            if (drawnCount >= MAX_LABELS) break


            layerLabelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchorPose,
                cameraPose,
                label.placeInfo
            )

            drawnCount++
        }
    }

    fun getExistingPlaceIds(): Set<Any> {
        return layerLabels.mapNotNull { it.placeInfo["place_id"] }.toSet()
    }


    fun getAllLabels(): List<LayerLabelInfo> {
        return layerLabels.toList()
    }
}
