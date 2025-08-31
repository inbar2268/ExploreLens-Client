package com.example.explorelens.ar.components

import android.os.Looper
import androidx.core.os.HandlerCompat
import com.example.explorelens.data.model.siteDetectionData.ARLabeledAnchor
import java.util.concurrent.Executors


typealias ARLabeledAnchorsCallback = (List<ARLabeledAnchor>) -> Unit
typealias EmptyCallback = () -> Unit


class ARAnchorsRepository private constructor() {

    private var mainHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    private var executor = Executors.newSingleThreadExecutor()

    companion object {
        val shared = ARAnchorsRepository()
    }

    fun getAlArLabeledAnchors(callback: ARLabeledAnchorsCallback) {
        executor.execute {
            val arLabeledAnchors = ARAnchorsLiveData.getAllAnchors()
            mainHandler.post {
                callback(arLabeledAnchors)
            }
        }
    }


    fun addArLabelAnchors(anchors: List<ARLabeledAnchor>, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsLiveData.addAnchors(anchors)
            mainHandler.post {
                callback()
            }
        }
    }


    fun addArLabelAnchor(anchor: ARLabeledAnchor, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsLiveData.addAnchor(anchor)
            mainHandler.post {
                callback()
            }
        }
    }

    fun deleteArLabeledAnchor(label: String, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsLiveData.deleteAnchor(label)
            mainHandler.post {
                callback()
            }
        }
    }
}