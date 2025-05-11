package com.example.explorelens.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CopyOnWriteArrayList

object ARAnchorsManager {

    private val anchors = CopyOnWriteArrayList<ARLabeledAnchor>()


    private val _anchorsLiveData = MutableLiveData<List<ARLabeledAnchor>>()
    val anchorsLiveData: LiveData<List<ARLabeledAnchor>> = _anchorsLiveData

    init {
        _anchorsLiveData.postValue(emptyList())
    }

    fun getAllAnchors(): List<ARLabeledAnchor> {
        return anchors.toList()
    }

    fun addAnchors(labeledAnchors: List<ARLabeledAnchor>) {
        labeledAnchors.forEach { labeledAnchor ->
            val existingIndex = anchors.indexOfFirst { it.label == labeledAnchor.label }
            if (existingIndex >= 0) {
                anchors[existingIndex] = labeledAnchor
            } else {
                anchors.add(labeledAnchor)
            }
        }
        _anchorsLiveData.postValue(anchors.toList())
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

    fun deleteAnchor(label: String) {
        val removed = anchors.removeIf { it.label == label }
        if (removed) {
            _anchorsLiveData.postValue(anchors.toList())
        }
    }

    fun clearAll() {
        anchors.clear()
        _anchorsLiveData.postValue(emptyList())
    }
}