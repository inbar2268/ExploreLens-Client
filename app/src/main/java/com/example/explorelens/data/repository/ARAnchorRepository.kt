package com.example.explorelens.data.repository

import android.os.Looper
import androidx.core.os.HandlerCompat
import com.example.explorelens.ar.components.ARAnchorStore
import com.example.explorelens.data.model.arLabel.ARLabeledAnchor
import java.util.concurrent.Executors

class ARAnchorRepository private constructor() {

    private var mainHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    private var executor = Executors.newSingleThreadExecutor()

    companion object {
        val shared = ARAnchorRepository()
    }

    fun getAlArLabeledAnchors(callback: (List<ARLabeledAnchor>) -> Unit) {
        executor.execute {
            val arLabeledAnchors = ARAnchorStore.getAllAnchors()
            mainHandler.post {
                callback(arLabeledAnchors)
            }
        }
    }

    fun addArLabelAnchor(anchor: ARLabeledAnchor, callback: () -> Unit = {}) {
        executor.execute {
            ARAnchorStore.addAnchor(anchor)
            mainHandler.post {
                callback()
            }
        }
    }

}