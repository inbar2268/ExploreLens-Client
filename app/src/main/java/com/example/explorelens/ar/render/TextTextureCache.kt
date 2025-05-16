package com.example.explorelens.ar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.util.Log
import com.example.explorelens.common.samplerender.GLError
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Texture
import java.nio.ByteBuffer

/**
 * Generates and caches GL textures for label names with iPhone notification-style design.
 */
class TextTextureCache(private val context: Context) {
  companion object {
    private const val TAG = "TextTextureCache"
  }

  private val cacheMap = mutableMapOf<String, Texture>()

  private val lato: Typeface? by lazy {
    try {
      Typeface.createFromAsset(context.assets, "fonts/Montserrat-Light.ttf")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load Lato-Italic.ttf", e)
      null
    }
  }

  private val montserrat: Typeface? by lazy {
    try {
      Typeface.createFromAsset(context.assets, "fonts/Montserrat-SemiBoldItalic.ttf")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load Montserrat-Light.ttf", e)
      null
    }
  }

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

  // Label text paint with larger size and custom font
  val labelPaint = Paint().apply {
    textSize = 70f // Increased from 36f
    color = Color.argb(255, 45, 45, 45) // Darker gray for better readability
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = montserrat ?: Typeface.create("sans-serif-black", Typeface.BOLD) // Fallback font
  }

  // Description text paint with custom font
  val descPaint = Paint().apply {
    textSize = 35f // Slightly increased
    color = Color.argb(200, 60, 60, 60) // Slightly transparent dark gray
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = lato ?: Typeface.create("sans-serif", Typeface.NORMAL) // Fallback font
  }

  // iPhone-style glassmorphism background with blur effect
  val bgPaint = Paint().apply {
    color = Color.argb(220, 255, 255, 255) // More transparent white
    style = Paint.Style.FILL
    isAntiAlias = true
    // For glassmorphism effect, we'll create multiple layers
  }

  // Thin white frame
  val framePaint = Paint().apply {
    color = Color.argb(255, 255, 255, 255) // Semi-transparent white
    style = Paint.Style.STROKE
    strokeWidth = 5f // Thin frame
    isAntiAlias = true
  }

  val innerFramePaint = Paint().apply {
    color = Color.argb(255, 255, 255, 255) // Slightly transparent
    style = Paint.Style.STROKE
    strokeWidth = 3f
    isAntiAlias = true
  }
  // Shadow paint for depth
  val shadowPaint = Paint().apply {
    color = Color.argb(10, 0, 0, 0) // Very light shadow
    style = Paint.Style.FILL
    isAntiAlias = true
    maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
  }

  // Inner glow effect
  val innerGlowPaint = Paint().apply {
    color = Color.argb(60, 255, 255, 255)
    style = Paint.Style.STROKE
    strokeWidth = 1f
    isAntiAlias = true
  }

  private fun generateBitmapFromString(string: String): Bitmap {
    Log.d(TAG, "Generating iPhone-style texture for: $string")

    // Parse the input string
    val parts = string.split("||", limit = 2)
    val label = parts[0].trim()
    val description = if (parts.size > 1) parts[1].trim() else ""

    // Larger dimensions for better quality
    val w = 900 // Increased width
    val h = 450 // Increased height

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.TRANSPARENT)

    val canvas = Canvas(bitmap)

    // Calculate text bounds
    val labelBounds = Rect()
    labelPaint.getTextBounds(label, 0, label.length, labelBounds)

    // Define padding and container
    val padding = 5f
    val containerRect = RectF(
      padding,
      padding,
      w - padding,
      h - padding
    )

    // Draw shadow first (behind the container)
    val shadowRect = RectF(containerRect)
    shadowRect.offset(3f, 3f) // Slight offset for shadow
    val cornerRadius = 22f // Larger corner radius for modern look
    canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)

    // Create layered glassmorphism effect
    // Layer 1: Base blur effect
    val blurPaint1 = Paint(bgPaint).apply {
      alpha = 180
    }
    canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, blurPaint1)

    // Layer 2: Lighter overlay for glassmorphism
    val blurPaint2 = Paint().apply {
      color = Color.argb(80, 255, 255, 255)
      style = Paint.Style.FILL
      isAntiAlias = true
    }
    canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, blurPaint2)

    // Draw thin white frame
    canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, framePaint)

    // Draw inner glow for depth
    val innerRect = RectF(containerRect)
    canvas.drawRoundRect(innerRect, cornerRadius - 3, cornerRadius - 3, innerFramePaint)

    innerRect.inset(1f, 1f)
    canvas.drawRoundRect(innerRect, cornerRadius - 1, cornerRadius - 1, innerGlowPaint)

    // Calculate text positioning
    val centerY = h / 2f
    val labelY = if (description.isEmpty()) {
      centerY + labelBounds.height() / 2f
    } else {
      centerY - 20f
    }

    // Draw label text with slight text shadow for depth
    val textShadowPaint = Paint(labelPaint).apply {
      color = Color.argb(30, 0, 0, 0)
    }
    canvas.drawText(label, w / 2f + 1f, labelY + 1f, textShadowPaint)
    canvas.drawText(label, w / 2f, labelY, labelPaint)

    // Draw description if it exists
    if (description.isNotEmpty()) {
      val firstLine = extractFirstLine(description)
      val availableWidth = w - (2 * padding + 40f)
      val formattedText = formatTextToFit(firstLine, descPaint, availableWidth)

      val descBounds = Rect()
      descPaint.getTextBounds(formattedText, 0, formattedText.length, descBounds)
      val descY = labelY + labelBounds.height() + 30f

      // Draw description text with shadow
      val descShadowPaint = Paint(descPaint).apply {
        color = Color.argb(20, 0, 0, 0)
      }

      formattedText.split("\n").forEachIndexed { index, line ->
        val lineY = descY + (index * descPaint.textSize * 1.2f)
        canvas.drawText(line, w / 2f + 1f, lineY + 1f, descShadowPaint)
        canvas.drawText(line, w / 2f, lineY, descPaint)
      }
    }

    Log.d(TAG, "Created iPhone-style bitmap: ${bitmap.width}x${bitmap.height}")
    return bitmap
  }

  // Extract the first sentence or line of text
  private fun extractFirstLine(text: String): String {
    val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), 0)
    return if (sentenceEnd >= 0) {
      text.substring(0, sentenceEnd + 1)
    } else {
      text
    }
  }

  // Format text to fit within available width
  private fun formatTextToFit(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) {
      return text
    }

    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
      val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

      if (paint.measureText(testLine) <= maxWidth) {
        currentLine = testLine
      } else {
        if (currentLine.isNotEmpty()) {
          lines.add(currentLine)
        }
        currentLine = word
      }
    }

    if (currentLine.isNotEmpty()) {
      lines.add(currentLine)
    }

    // Limit to 2 lines for iPhone notification style
    if (lines.size > 2) {
      val lastLine = lines[1]
      lines[1] = if (lastLine.length > 3) {
        lastLine.substring(0, lastLine.length - 3) + "..."
      } else {
        lastLine + "..."
      }
      return lines.take(2).joinToString("\n")
    }

    return lines.joinToString("\n")
  }
}