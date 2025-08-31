package com.example.explorelens.model

import com.google.ar.core.Pose

class Snapshot (
    val timestamp: Long,
    val cameraPose: Pose,
    val viewMatrix: FloatArray,
    val projectionMatrix: FloatArray
)