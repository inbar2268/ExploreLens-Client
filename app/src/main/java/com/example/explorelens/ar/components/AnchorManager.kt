package com.example.explorelens.ar.components

import android.util.Log
import com.example.explorelens.ArActivity
import com.example.explorelens.Model
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.example.explorelens.data.model.siteDetectionData.SiteInformation
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.model.ARLabeledAnchor
import com.example.explorelens.model.Snapshot
import com.google.ar.core.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.sqrt

class AnchorManager(
    private val activity: ArActivity,
    private val view: ArActivityView,
    private val networkScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AnchorManager"
    }

    var arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
        private set

    private val reusableAnchorList = ArrayList<ARLabeledAnchor>()
    private var hasLoadedAnchors = false
    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    fun initialize() {
        if (!hasLoadedAnchors) {
            getAllAnchors()
            hasLoadedAnchors = true
        }
    }

    fun getAnchorsForRendering(): List<ARLabeledAnchor> {
        reusableAnchorList.clear()
        synchronized(arLabeledAnchors) {
            reusableAnchorList.addAll(arLabeledAnchors)
        }
        return reusableAnchorList
    }

    fun createAnchorFromAnalyzedResult(
        session: Session,
        snapshotData: Snapshot,
        result: ImageAnalyzedResult,
        frame: Frame
    ): ARLabeledAnchor? {
        val siteInfo = result.siteInformation
        val siteId = result.siteInfoId

        if (!isValidSiteData(siteInfo, siteId)) return null

        val anchor = createAnchor(session, snapshotData.cameraPose, siteInfo!!.x, siteInfo.y, frame)
            ?: return null

        val initialLabel = "${siteInfo.siteName}||Loading..."
        val arLabeledAnchor = ARLabeledAnchor(anchor, initialLabel, siteInfo.siteName).apply {
            this.siteId = siteId
        }

        fetchAndUpdateSiteDetails(arLabeledAnchor, siteInfo.siteName, siteId)
        return arLabeledAnchor
    }

    fun addAnchor(anchor: ARLabeledAnchor) {
        synchronized(arLabeledAnchors) {
            arLabeledAnchors.add(anchor)
        }
        addAnchorToDatabase(anchor)
    }

    fun clear() {
        synchronized(arLabeledAnchors) {
            arLabeledAnchors.forEach { it.anchor.detach() }
            arLabeledAnchors.clear()
        }
    }

    private fun createAnchor(
        session: Session,
        cameraPose: Pose,
        atX: Float,
        atY: Float,
        frame: Frame
    ): Anchor? {
        return placeLabelAccurateWithSnapshot(session, cameraPose, atX, atY, frame).also {
            if (it == null) {
                Log.e(TAG, "Failed to place anchor")
            }
        }
    }

    private fun placeLabelAccurateWithSnapshot(
        session: Session,
        snapshotPose: Pose,
        labelX: Float,
        labelY: Float,
        frame: Frame
    ): Anchor? {
        Log.d(TAG, "Input coordinates (normalized): x=$labelX, y=$labelY")
        Log.d(TAG, "Snapshot pose: tx=${snapshotPose.tx()}, ty=${snapshotPose.ty()}, tz=${snapshotPose.tz()}")

        // Convert normalized coordinates (0-1) to view space (-1 to 1)
        val viewportX = (labelX * 2.0f) - 1.0f
        val viewportY = -((labelY * 2.0f) - 1.0f)

        // Direction vector in camera space
        val directionVector = floatArrayOf(viewportX, viewportY, -1.0f)

        // Transform direction vector to world space using snapshot pose
        val worldDirection = snapshotPose.transformPoint(directionVector)

        // Normalize the direction vector
        val magnitude = sqrt(
            worldDirection[0] * worldDirection[0] +
                    worldDirection[1] * worldDirection[1] +
                    worldDirection[2] * worldDirection[2]
        )

        val normalizedDirection = floatArrayOf(
            worldDirection[0] / magnitude,
            worldDirection[1] / magnitude,
            worldDirection[2] / magnitude
        )

        // Convert to image pixel coordinates for hit test
        val imageWidth = frame.camera.imageIntrinsics.imageDimensions[0].toFloat()
        val imageHeight = frame.camera.imageIntrinsics.imageDimensions[1].toFloat()

        val pixelX = labelX * imageWidth
        val pixelY = labelY * imageHeight

        // Convert to view coordinates
        convertFloats[0] = pixelX
        convertFloats[1] = pixelY
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Perform hit test
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        var distance = 2.0f

        if (hits.isNotEmpty()) {
            val hitResult = hits.first()
            distance = hitResult.distance
            Log.d(TAG, "Hit test succeeded: distance=$distance")
        } else {
            Log.d(TAG, "Hit test failed, using default distance: $distance")
        }

        // Calculate final world position
        val worldPosition = floatArrayOf(
            snapshotPose.tx() + normalizedDirection[0] * distance,
            snapshotPose.ty() + normalizedDirection[1] * distance,
            snapshotPose.tz() + normalizedDirection[2] * distance
        )

        // Create anchor with final position
        val anchorPose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])

        Log.d(TAG, "Created anchor at: tx=${anchorPose.tx()}, ty=${anchorPose.ty()}, tz=${anchorPose.tz()}")

        return session.createAnchor(anchorPose)
    }

    private fun isValidSiteData(siteInfo: SiteInformation?, siteId: String?): Boolean {
        if (siteInfo?.x == null || siteInfo.y == null || siteInfo.siteName == null || siteId.isNullOrBlank()) {
            Log.e(TAG, "Missing location data or site name/id")
            return false
        }
        return true
    }

    private fun fetchAndUpdateSiteDetails(
        anchor: ARLabeledAnchor,
        siteName: String,
        siteId: String
    ) {
        Log.d(TAG, "Fetching site details for ID: $siteId")
        networkScope.launch {
            val context = activity.applicationContext
            val repository = SiteDetailsRepository(context)
            try {
                val siteInfo: SiteDetails? = suspendCancellableCoroutine { continuation ->
                    repository.fetchSiteDetails(
                        siteId = siteId,
                        onSuccess = { fetchedSiteInfo ->
                            Log.d(TAG, "Repository fetch successful for $siteId")
                            continuation.resume(fetchedSiteInfo)
                        },
                        onError = {
                            Log.e(TAG, "Repository fetch failed for $siteId")
                            continuation.resume(null)
                        }
                    )
                }
                withContext(Dispatchers.Main) {
                    if (siteInfo != null) {
                        Log.d(TAG, "Updating UI with site details for $siteId")
                        updateAnchorWithDetails(anchor, siteName, siteInfo, siteId)
                    } else {
                        Log.e(TAG, "No site details received for $siteId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching site details for $siteId: ${e.message}", e)
            }
        }
    }

    private fun updateAnchorWithDetails(
        oldAnchor: ARLabeledAnchor,
        siteName: String,
        siteInfo: SiteDetails,
        siteId: String
    ) {
        val previewText = extractPreviewText(siteInfo.description)
        val newLabelText = "$siteName||$previewText"

        Log.d(TAG, "Updated label with description: $newLabelText")

        synchronized(arLabeledAnchors) {
            val index = arLabeledAnchors.indexOf(oldAnchor)
            if (index != -1) {
                val updatedAnchor = ARLabeledAnchor(oldAnchor.anchor, newLabelText, siteName).apply {
                    this.fullDescription = siteInfo.description
                    this.siteId = siteId
                }
                arLabeledAnchors[index] = updatedAnchor
            }
        }
    }

    private fun extractPreviewText(description: String): String {
        if (description.isEmpty()) return ""

        val firstSentenceEnd = description.indexOfAny(charArrayOf('.', '!', '?'), 0)
        if (firstSentenceEnd >= 0 && firstSentenceEnd < 200) {
            return description.substring(0, firstSentenceEnd + 1).trim()
        }

        val firstLineEnd = description.indexOf('\n')
        if (firstLineEnd >= 0 && firstLineEnd < 200) {
            return description.substring(0, firstLineEnd).trim()
        }

        if (description.length > 200) {
            val lastSpace = description.substring(0, 200).lastIndexOf(' ')
            return if (lastSpace > 0) {
                description.substring(0, lastSpace).trim() + "..."
            } else {
                description.substring(0, 200).trim() + "..."
            }
        }

        return description.trim()
    }

    private fun getAllAnchors() {
        Model.shared.getAlArLabeledAnchors { fetchedAnchors ->
            synchronized(arLabeledAnchors) {
                arLabeledAnchors.clear()
                arLabeledAnchors.addAll(fetchedAnchors)
            }
        }
    }

    private fun addAnchorToDatabase(anchor: ARLabeledAnchor) {
        Model.shared.addArLabelAnchor(anchor) {
            getAllAnchors()
        }
    }
}