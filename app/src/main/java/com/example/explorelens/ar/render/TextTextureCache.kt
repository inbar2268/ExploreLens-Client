package com.example.explorelens.ar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

  // Simple paint for label text (bright red to be visible)
  val textPaint = Paint().apply {
    textSize = 42f // Larger text size for visibility
    color = Color.RED // Bright red for visibility
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
  }

  // Black outline for text readability
  val strokePaint = Paint(textPaint).apply {
    color = Color.BLACK
    style = Paint.Style.STROKE
    strokeWidth = 4f // Thicker stroke for better visibility
  }

  private fun generateBitmapFromString(string: String): Bitmap {
    Log.d(TAG, "Generating texture for: $string")

    // Parse the input string (if it contains the separator)
    val parts = string.split("||", limit = 2)
    val label = parts[0].trim()
    val description = if (parts.size > 1) parts[1].trim() else ""

    val w = 512 // Wider
    val h = 256

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // Fill with semi-transparent black for the container background
    bitmap.eraseColor(Color.argb(180, 0, 0, 0))

    val canvas = Canvas(bitmap)

    // Draw label text with outline
    canvas.drawText(label, w / 2f, h / 3f, strokePaint)
    canvas.drawText(label, w / 2f, h / 3f, textPaint)

    // Draw description if it exists (with smaller font)
    if (description.isNotEmpty()) {
      val descPaint = Paint(textPaint).apply {
        textSize = 32f
        color = Color.WHITE
        typeface = Typeface.DEFAULT
      }

      val descStrokePaint = Paint(strokePaint).apply {
        textSize = 32f
      }

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

      canvas.drawText(truncatedDesc, w / 2f, h * 2/3f, descStrokePaint)
      canvas.drawText(truncatedDesc, w / 2f, h * 2/3f, descPaint)
    }

    // Log bitmap dimensions for debugging
    Log.d(TAG, "Created bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

    return bitmap
  }
}