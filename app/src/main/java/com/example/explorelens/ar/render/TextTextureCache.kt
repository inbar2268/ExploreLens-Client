package com.example.explorelens.ar.render

import android.graphics.Bitmap
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
    textSize = 36f // REDUCED text size for better fit
    color = Color.BLACK
    style = Paint.Style.FILL
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
  }

  // Smaller text for the description
  val descPaint = Paint().apply {
    textSize = 26f // REDUCED text size for description
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

    // Make bitmap dimensions larger to accommodate longer description line
    val w = 850 // Width increased
    val h = 420 // Increased height for better text positioning

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // Start with transparent background
    bitmap.eraseColor(Color.TRANSPARENT)

    val canvas = Canvas(bitmap)

    // Calculate text bounds to ensure proper positioning
    val labelBounds = Rect()
    labelPaint.getTextBounds(label, 0, label.length, labelBounds)

    // Calculate vertical offsets based on text height to prevent cutoff
    val titleOffset = labelBounds.height() * 1.5f

    // Define the container rectangle with padding - make it larger
    val padding = 50f
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

    // Center the label vertically and horizontally
    // Position text based on its measured height to avoid cutoff
    val labelY = h * 0.38f + titleOffset/2
    canvas.drawText(label, w / 2f, labelY, labelPaint)

    // Draw description if it exists - position it properly below the title
    if (description.isNotEmpty()) {
      // Get first line by looking for period, exclamation, question mark, or newline
      val firstLine = extractFirstLine(description)

      // Get the available width for the description (container width minus padding)
      val availableWidth = w - (2 * padding + 20f)  // Extra 20px margin on sides

      // Format the text to fit within the available width
      val formattedText = formatTextToFit(firstLine, descPaint, availableWidth)

      // Position description below the title with proper spacing
      val descBounds = Rect()
      descPaint.getTextBounds(formattedText, 0, formattedText.length, descBounds)
      val descY = labelY + labelBounds.height() + descBounds.height() + 20f

      // Draw each line of the formatted text
      formattedText.split("\n").forEachIndexed { index, line ->
        val lineY = descY + (index * descPaint.textSize * 1.2f)
        canvas.drawText(line, w / 2f, lineY, descPaint)
      }
    }

    Log.d(TAG, "Created bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

    return bitmap
  }

  // Extract the first sentence or line of text
  private fun extractFirstLine(text: String): String {
    // Try to get the first sentence
    val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), 0)
    return if (sentenceEnd >= 0) {
      // Include the punctuation mark
      text.substring(0, sentenceEnd + 1)
    } else {
      // If no sentence-ending punctuation found, use the whole text
      text
    }
  }

  // Format text to fit within available width, with proper line breaks
  private fun formatTextToFit(text: String, paint: Paint, maxWidth: Float): String {
    // If text already fits, return it as is
    if (paint.measureText(text) <= maxWidth) {
      return text
    }

    // If text is too long, break it into multiple lines
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
      val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

      if (paint.measureText(testLine) <= maxWidth) {
        currentLine = testLine
      } else {
        // Current line is full, start a new line
        if (currentLine.isNotEmpty()) {
          lines.add(currentLine)
        }
        currentLine = word
      }
    }

    // Add the last line if it's not empty
    if (currentLine.isNotEmpty()) {
      lines.add(currentLine)
    }

    // Limit to 2 lines maximum to fit in the container
    if (lines.size > 3) {
      val lastLine = lines[1]
      // Add ellipsis if we're truncating
      lines[1] = if (lastLine.length > 3) {
        lastLine.substring(0, lastLine.length - 3) + "..."
      } else {
        lastLine + "..."
      }
      return lines.take(3).joinToString("\n")
    }

    return lines.joinToString("\n")
  }
}