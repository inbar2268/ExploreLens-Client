package com.example.explorelens.ar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.util.Log
import com.example.explorelens.common.samplerender.GLError
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Texture
import java.nio.ByteBuffer

/**
 * Generates and caches GL textures for label names.
 */
class TextTextureCache {
  companion object {
    private const val TAG = "TextTextureCache"
  }

  private val cacheMap = mutableMapOf<String, Texture>()

  /**
   * Get a texture for a given string. If that string hasn't been used yet, create a texture for it
   * and cache the result.
   */
  fun get(render: SampleRender, string: String): Texture {
    return cacheMap.computeIfAbsent(string) {
      generateTexture(render, string)
    }
  }

  private fun generateTexture(render: SampleRender, string: String): Texture {
    val texture = Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE)

    val bitmap = generateBitmapFromString(string)
    val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    buffer.rewind()

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.textureId)
    GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture")
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_RGBA8,
      bitmap.width,
      bitmap.height,
      0,
      GLES30.GL_RGBA,
      GLES30.GL_UNSIGNED_BYTE,
      buffer
    )
    GLError.maybeThrowGLException("Failed to populate texture data", "glTexImage2D")
    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    GLError.maybeThrowGLException("Failed to generate mipmaps", "glGenerateMipmap")

    return texture
  }

  // Clean, elegant text for the label
  val labelPaint = Paint().apply {
    textSize = 40f
    color = Color.BLACK
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
  }

  // Smaller text for the description
  val descPaint = Paint().apply {
    textSize = 30f
    color = Color.BLACK
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
  }

  // Background container paint
  val bgPaint = Paint().apply {
    color = Color.argb(220, 255, 255, 255) // Semi-transparent white
    style = Paint.Style.FILL
    isAntiAlias = true
  }

  // Light gray outline for the container
  val outlinePaint = Paint().apply {
    color = Color.argb(255, 200, 200, 200) // Light gray
    style = Paint.Style.STROKE
    strokeWidth = 3f
    isAntiAlias = true
  }

  private fun generateBitmapFromString(string: String): Bitmap {
    Log.d(TAG, "Generating texture for: $string")

    // Parse the input string (if it contains the separator)
    val parts = string.split("||", limit = 2)
    val label = parts[0].trim()
    val description = if (parts.size > 1) parts[1].trim() else ""

    val w = 512 // Width
    val h = 256 // Height

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // Start with transparent background
    bitmap.eraseColor(Color.TRANSPARENT)

    val canvas = Canvas(bitmap)

    // Define the container rectangle with padding
    val padding = 20f
    val containerRect = RectF(
      padding,
      padding,
      w - padding,
      h - padding
    )

    // Draw rounded rectangle for the container
    val cornerRadius = 30f // Large corner radius for modern look
    canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, bgPaint)
    canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, outlinePaint)

    // Draw label with elegant font
    canvas.drawText(label, w / 2f, h * 0.38f, labelPaint)

    // Draw description if it exists
    if (description.isNotEmpty()) {
      // Truncate text if needed
      val maxWidth = w * 0.85f
      val truncatedDesc = if (descPaint.measureText(description) > maxWidth) {
        var shortened = description
        while (shortened.isNotEmpty() && descPaint.measureText(shortened + "...") > maxWidth) {
          shortened = shortened.substring(0, shortened.length - 1)
        }
        shortened + "..."
      } else {
        description
      }

      canvas.drawText(truncatedDesc, w / 2f, h * 0.65f, descPaint)
    }

    Log.d(TAG, "Created bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

    return bitmap
  }
}