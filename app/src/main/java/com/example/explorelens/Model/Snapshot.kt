package com.example.explorelens.Model

import android.graphics.Bitmap
import com.google.ar.core.Pose

class Snapshot (
    val timestamp: Long,
    val cameraPose: Pose,
    val viewMatrix: FloatArray,
    val projectionMatrix: FloatArray
)