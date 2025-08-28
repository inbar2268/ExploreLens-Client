package com.example.explorelens.ar.components

import androidx.lifecycle.MutableLiveData
import com.example.explorelens.data.model.arLabel.ARLabeledAnchor
import java.util.concurrent.CopyOnWriteArrayList

object ARAnchorStore {

    private val anchors = CopyOnWriteArrayList<ARLabeledAnchor>()
    private val _anchorsLiveData = MutableLiveData<List<ARLabeledAnchor>>()

    init {
        _anchorsLiveData.postValue(emptyList())
    }

    fun getAllAnchors(): List<ARLabeledAnchor> {
        return anchors.toList()
    }


    fun addAnchor(labeledAnchor: ARLabeledAnchor) {
        val existingIndex = anchors.indexOfFirst { it.label == labeledAnchor.label }
        if (existingIndex >= 0) {
            anchors[existingIndex] = labeledAnchor
        } else {
            anchors.add(labeledAnchor)
        }
        _anchorsLiveData.postValue(anchors.toList())
    }

}