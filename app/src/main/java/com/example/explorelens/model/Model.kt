package com.example.explorelens

import android.os.Looper
import androidx.core.os.HandlerCompat
import com.example.explorelens.model.ARAnchorsManager
import com.example.explorelens.model.ARLabeledAnchor
import java.util.concurrent.Executors

typealias ARLabeledAnchorsCallback = (List<ARLabeledAnchor>) -> Unit
typealias EmptyCallback = () -> Unit

class Model private constructor() {

    private var mainHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    private var executor = Executors.newSingleThreadExecutor()

    companion object {
        val shared = Model()
    }

    fun getAlArLabeledAnchors(callback: ARLabeledAnchorsCallback) {
        executor.execute {
            val arLabeledAnchors = ARAnchorsManager.getAllAnchors()
            mainHandler.post {
                callback(arLabeledAnchors)
            }
        }
    }


    fun addArLabelAnchors(anchors: List<ARLabeledAnchor>, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsManager.addAnchors(anchors)
            mainHandler.post {
                callback()
            }
        }
    }


    fun addArLabelAnchor(anchor: ARLabeledAnchor, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsManager.addAnchor(anchor) // נניח שאת יוצרת פונקציה חדשה addAnchor בתוך ARAnchorsManager
            mainHandler.post {
                callback()
            }
        }
    }

    fun deleteArLabeledAnchor(label: String, callback: EmptyCallback) {
        executor.execute {
            ARAnchorsManager.deleteAnchor(label)
            mainHandler.post {
                callback()
            }
        }
    }
}