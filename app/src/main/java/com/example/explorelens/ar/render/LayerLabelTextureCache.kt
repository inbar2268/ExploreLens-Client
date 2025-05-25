
package com.example.explorelens.ar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.opengl.GLES30
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.explorelens.R
import com.example.explorelens.common.samplerender.GLError
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Texture
import java.nio.ByteBuffer

/**
 * Generates and caches GL textures for nearby place labels with a frosted glass design.
 */
class LayerLabelTextureCache(private val context: Context) {
    companion object {
        private const val TAG = "LayerLabelTextureCache"
    }

    private val cacheMap = mutableMapOf<String, Texture>()

    private val robotoRegular: Typeface? by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/Roboto-Regular.ttf")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Roboto-Regular.ttf", e)
            null
        }
    }

    private val robotoBold: Typeface? by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/Roboto-Bold.ttf")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Roboto-Bold.ttf", e)
            null
        }
    }

    // Regular icons
    private val starIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_star)
    }

    private val phoneIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_phone)
    }

    private val clockIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_clock)
    }

    // Type-specific icons with larger size
    private val restaurantIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_restaurant, 48, 48)
    }

    private val cafeIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_cafe, 48, 48)
    }

    private val barIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_bar, 48, 48)
    }

    private val bakeryIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_bakery, 48, 48)
    }

    private val hotelIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_hotel, 48, 48)
    }

    private val pharmacyIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_pharmacy, 48, 48)
    }

    private val gymIcon: Bitmap? by lazy {
        loadIconFromResource(R.drawable.ic_gym, 48, 48)
    }

    // Helper function to load icon from resource
    private fun loadIconFromResource(resId: Int, width: Int = 36, height: Int = 36): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, resId)
            drawableToBitmap(drawable, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon resource: $resId", e)
            null
        }
    }

    // Map of type to icon
    private val typeIcons = mapOf(
        "restaurant" to restaurantIcon,
        "cafe" to cafeIcon,
        "bar" to barIcon,
        "bakery" to bakeryIcon,
        "hotel" to hotelIcon,
        "pharmacy" to pharmacyIcon,
        "gym" to gymIcon
    )

    /**
     * Get a texture for a given place info. If that info hasn't been used yet, create a texture for it
     * and cache the result.
     */
    fun get(render: SampleRender, placeInfo: Map<String, Any?>): Texture {
        // Create a unique key based on the place info
        val key = "place_${placeInfo["place_id"]}"

        return cacheMap.computeIfAbsent(key) {
            generateTexture(render, placeInfo)
        }
    }

    private fun generateTexture(render: SampleRender, placeInfo: Map<String, Any?>): Texture {
        val texture = Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE)

        val bitmap = generateBitmapFromPlaceInfo(placeInfo)
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

    // Title text paint (large size for the place name)
    private val titlePaint = Paint().apply {
        textSize = 70f
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoBold ?: Typeface.DEFAULT_BOLD
    }

    // Details text paint (for phone number, rating)
    private val detailPaint = Paint().apply {
        textSize = 45f
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoRegular ?: Typeface.DEFAULT
    }

    // Status text paint (for open/closed status)
    private val statusPaint = Paint().apply {
        textSize = 40f
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoRegular ?: Typeface.DEFAULT
    }

    // Outer frame
    private val framePaint = Paint().apply {
        color = Color.argb(255, 255, 255, 255) // Fully opaque white
        style = Paint.Style.STROKE
        strokeWidth = 5f // Thicker frame
        isAntiAlias = true
    }

    // Frosted glass background
    private val backgroundPaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255) // Translucent white
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Divider line
    private val dividerPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0) // Light gray
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private fun generateBitmapFromPlaceInfo(placeInfo: Map<String, Any?>): Bitmap {
        Log.d(TAG, "Generating frosted label for: ${placeInfo["name"]}")

        // Extract data from place info
        val name = placeInfo["name"] as? String ?: "Unknown Place"
        val type = placeInfo["type"] as? String ?: "unknown"
        val rating = placeInfo["rating"] as? Double ?: 0.0
        val phoneNumber = placeInfo["phone_number"] as? String ?: "No phone"

        // Extract opening hours
        @Suppress("UNCHECKED_CAST")
        val openingHours = placeInfo["opening_hours"] as? Map<String, Any>
        val isOpen = openingHours?.get("open_now") as? Boolean ?: false
        val statusText = if (isOpen) "Open" else "Closed"
        val statusColor = if (isOpen) Color.rgb(0, 140, 0) else Color.rgb(200, 0, 0) // Green for open, red for closed

        // Get the appropriate icon for this place type
        val typeIcon = typeIcons[type.toLowerCase()]

        // Fixed dimensions for this label style - narrower width
        val width = 720 // Reduced from 900
        val height = 500

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(bitmap)

        // Draw rounded rectangle with frosted glass effect
        val bgRect = RectF(20f, 20f, width - 20f, height - 20f)

        // Draw shadow/glow
        val shadowPaint = Paint(backgroundPaint).apply {
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            color = Color.argb(40, 0, 0, 0)
        }
        canvas.drawRoundRect(bgRect, 40f, 40f, shadowPaint)

        // Draw background
        canvas.drawRoundRect(bgRect, 40f, 40f, backgroundPaint)

        // Draw outer frame
        canvas.drawRoundRect(bgRect, 40f, 40f, framePaint)

        // Start position for text
        var yPosition = 110f

        // Draw type icon and place name
        val nameX = if (typeIcon != null) {
            // Draw type icon to the left of the name
            canvas.drawBitmap(typeIcon, 60f, yPosition - 40f, null)
            // Offset the name text
            120f
        } else {
            // No icon, start text at normal position
            60f
        }

        // Draw place name
        canvas.drawText(name, nameX, yPosition, titlePaint)
        yPosition += 70f

        // Draw divider line
        canvas.drawLine(60f, yPosition, width - 60f, yPosition, dividerPaint)
        yPosition += 50f

        // Draw status with clock icon
        statusPaint.color = statusColor
        val statusX = 60f
        clockIcon?.let {
            canvas.drawBitmap(it, statusX, yPosition - 30f, null)
            canvas.drawText(statusText, statusX + 45f, yPosition, statusPaint)
        } ?: canvas.drawText("$statusText", statusX, yPosition, statusPaint)
        yPosition += 65f

        // Draw phone number with icon
        phoneIcon?.let {
            canvas.drawBitmap(it, 60f, yPosition - 30f, null)
            canvas.drawText(phoneNumber, 110f, yPosition, detailPaint)
        } ?: canvas.drawText("📞 $phoneNumber", 60f, yPosition, detailPaint)
        yPosition += 65f

        // Draw rating with star icon
        val ratingText = rating.toString()
        starIcon?.let {
            canvas.drawBitmap(it, 60f, yPosition - 30f, null)
            canvas.drawText(ratingText, 110f, yPosition, detailPaint)
        } ?: canvas.drawText("★ $ratingText", 60f, yPosition, detailPaint)

        Log.d(TAG, "Created frosted label bitmap: ${bitmap.width}x${bitmap.height}")
        return bitmap
    }

    // Helper function to convert drawable to bitmap
    private fun drawableToBitmap(drawable: Drawable?, width: Int, height: Int): Bitmap? {
        if (drawable == null) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}