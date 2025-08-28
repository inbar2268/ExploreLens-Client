
package com.example.explorelens.ar.image

import android.graphics.Bitmap
import android.graphics.Matrix

object ImageUtils {
  /**
   * Creates a new [Bitmap] by rotating the input bitmap [rotation] degrees.
   * If [rotation] is 0, the input bitmap is returned.
   */
  fun rotateBitmap(bitmap: Bitmap, rotation: Int): Bitmap {
    if (rotation == 0) return bitmap

    val matrix = Matrix()
    matrix.postRotate(rotation.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
  }
}