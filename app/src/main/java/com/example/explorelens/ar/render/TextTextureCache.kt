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
    color = Color.argb(255, 45, 45, 45) // Slightly transparent dark gray
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

  private fun truncateTextToFit(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) {
      return text
    }

    val ellipsis = "..."
    val ellipsisWidth = paint.measureText(ellipsis)

    var truncatedText = ""
    var currentWidth = 0f

    for (i in text.indices) {
      val charWidth = paint.measureText(text.substring(i, i + 1))
      if (currentWidth + charWidth + ellipsisWidth <= maxWidth) {
        currentWidth += charWidth
        truncatedText += text[i]
      } else {
        break
      }
    }

    if (truncatedText.isEmpty()) {
      val firstWord = text.split(" ")[0]
      if (paint.measureText(firstWord) > maxWidth) {
        // Truncate the word itself
        for (i in firstWord.indices) {
          if (paint.measureText(firstWord.substring(0, i + 1) + ellipsis) > maxWidth) {
            return firstWord.substring(0, i) + ellipsis
          }
        }
      }
    }

    return truncatedText.trim() + ellipsis
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

    val containerPadding = 20f
    val textPadding = 40f

    val containerRect = RectF(
      containerPadding,
      containerPadding,
      w - containerPadding,
      h - containerPadding
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


    // Calculate the CORRECT maximum width for the text, using the internal textPadding
    val maxLabelTextWidth = containerRect.width() - (2 * textPadding)
    val scaledLabelPaint = Paint(labelPaint)

    // Start with the default size
    var currentTextSize = labelPaint.textSize
    scaledLabelPaint.textSize = currentTextSize

    // Find the optimal font size by reducing it until the text fits
    while (scaledLabelPaint.measureText(label) > maxLabelTextWidth && currentTextSize > 30f) {
      currentTextSize -= 1f
      scaledLabelPaint.textSize = currentTextSize
    }

    // Calculate text positioning with the SCALED paint
    val labelBounds = Rect()
    scaledLabelPaint.getTextBounds(label, 0, label.length, labelBounds)

    val topOffset = 60f // Increased from 40f to move label lower
    val labelY = if (description.isEmpty()) {
      // If no description, center the label
      h / 2f + labelBounds.height() / 2f
    } else {
      // Position label moderately high, not too close to top
      containerRect.top + topOffset + labelBounds.height()
    }

    // Draw label text using the SCALED paint
    val textShadowPaint = Paint(scaledLabelPaint).apply {
      color = Color.argb(30, 0, 0, 0)
    }
    canvas.drawText(label, w / 2f + 1f, labelY + 1f, textShadowPaint)
    canvas.drawText(label, w / 2f, labelY, scaledLabelPaint)

    // --- END OF CORRECTED FONT SCALING AND DRAWING LOGIC ---

    // Draw description if it exists
    if (description.isNotEmpty()) {
      val firstLine = extractFirstSentencesCompletely(description)
      // Use the same consistent width calculation for the description
      val availableDescWidth = containerRect.width() - (2 * textPadding)
      val formattedText = formatTextToFit(firstLine, descPaint, availableDescWidth, true)

      val descBounds = Rect()
      descPaint.getTextBounds(formattedText, 0, formattedText.length, descBounds)

      // Position description with even more spacing below the label
      val descY = labelY + 70f // Increased spacing from 50f to 70f

      // Draw description text with shadow
      val descShadowPaint = Paint(descPaint).apply {
        color = Color.argb(20, 0, 0, 0)
      }

      formattedText.split("\n").forEachIndexed { index, line ->
        val lineY = descY + (index * descPaint.textSize * 1.2f) // Increased line spacing
        canvas.drawText(line, w / 2f + 1f, lineY + 1f, descShadowPaint)
        canvas.drawText(line, w / 2f, lineY, descPaint)
      }
    }

    Log.d(TAG, "Created iPhone-style bitmap: ${bitmap.width}x${bitmap.height}")
    return bitmap
  }

  // Extract full sentences without cutting them off
  private fun extractFirstSentencesCompletely(text: String): String {
    if (text.isEmpty()) return ""

    // Find all sentence endings
    val sentenceEndings = mutableListOf<Int>()
    var startIndex = 0

    // Find all periods, exclamation points, and question marks
    while (startIndex < text.length) {
      val nextEnding = text.indexOfAny(charArrayOf('.', '!', '?'), startIndex)
      if (nextEnding < 0) break

      // Include the punctuation mark and check if there's a space after it
      val endingPos = if (nextEnding + 1 < text.length && text[nextEnding + 1] == ' ')
        nextEnding + 1 else nextEnding

      sentenceEndings.add(endingPos)
      startIndex = endingPos + 1
    }

    // If we found at least one sentence ending
    if (sentenceEndings.isNotEmpty()) {
      // Take either the first sentence if it's very long, or up to two sentences
      if (sentenceEndings[0] > 350) {
        // If first sentence is very long, take just part of it
        val cutoff = text.substring(0, 350)
        val lastSpace = cutoff.lastIndexOf(' ')
        return if (lastSpace > 0) {
          text.substring(0, lastSpace).trim() + "..."
        } else {
          cutoff + "..."
        }
      } else if (sentenceEndings.size > 1 && sentenceEndings[1] < 500) {
        // Take first two sentences if they're not too long together
        return text.substring(0, sentenceEndings[1] + 1).trim()
      } else {
        // Otherwise just take the first full sentence
        return text.substring(0, sentenceEndings[0] + 1).trim()
      }
    }

    // If no sentence endings found, check for line breaks
    val lineEnd = text.indexOf('\n')
    if (lineEnd >= 0) {
      return text.substring(0, lineEnd).trim()
    }

    // If text is too long without any breaks, find a good cutoff point
    if (text.length > 500) {
      val cutoff = text.substring(0, 500)
      val lastSpace = cutoff.lastIndexOf(' ')
      return if (lastSpace > 0) {
        text.substring(0, lastSpace).trim() + "..."
      } else {
        cutoff + "..."
      }
    }

    // If all else fails, return the whole text
    return text
  }

  // Enhanced text fitting with better word boundary detection
  private fun formatTextToFit(text: String, paint: Paint, maxWidth: Float, allowThreeLines: Boolean = false): String {
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
        // Check if the word itself is too long for the line
        if (paint.measureText(word) > maxWidth) {
          // If word is too long, break it with hyphen
          var wordPart = ""
          for (char in word) {
            val testPart = wordPart + char
            if (paint.measureText(testPart + "-") <= maxWidth) {
              wordPart = testPart
            } else {
              if (wordPart.isNotEmpty()) {
                lines.add(wordPart + "-")
                wordPart = char.toString()
              }
            }
          }
          currentLine = wordPart
        } else {
          currentLine = word
        }
      }
    }

    if (currentLine.isNotEmpty()) {
      lines.add(currentLine)
    }

    // Limit to allowed number of lines
    val maxLines = if (allowThreeLines) 3 else 2

    if (lines.size > maxLines) {
      val lastLine = lines[maxLines - 1]
      // Better truncation - ensure we don't cut in middle of word
      val words = lastLine.split(" ")
      if (words.size > 1) {
        val truncated = words.dropLast(1).joinToString(" ") + "..."
        lines[maxLines - 1] = truncated
      } else {
        lines[maxLines - 1] = if (lastLine.length > 5) {
          lastLine.substring(0, lastLine.length - 3) + "..."
        } else {
          lastLine + "..."
        }
      }
      return lines.take(maxLines).joinToString("\n")
    }

    return lines.joinToString("\n")
  }
}