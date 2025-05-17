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
import android.graphics.drawable.Drawable
import android.opengl.GLES30
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.explorelens.R
import com.example.explorelens.common.samplerender.GLError
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Texture
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates and caches GL textures for nearby place labels with a modern design.
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

    // Icons for the label
    private val starIcon: Bitmap? by lazy {
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_star)
            drawableToBitmap(drawable, 24, 24)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load star icon", e)
            null
        }
    }

    private val phoneIcon: Bitmap? by lazy {
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_phone)
            drawableToBitmap(drawable, 24, 24)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load phone icon", e)
            null
        }
    }

    private val typeIcon: Bitmap? by lazy {
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_clock)
            drawableToBitmap(drawable, 24, 24)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load category icon", e)
            null
        }
    }

    /**
     * Get a texture for a given place info. If that info hasn't been used yet, create a texture for it
     * and cache the result.
     */
    fun get(render: SampleRender, placeInfo: Map<String, Any>): Texture {
        // Create a unique key based on the place info
        val key = "place_${placeInfo["place_id"]}"

        return cacheMap.computeIfAbsent(key) {
            generateTexture(render, placeInfo)
        }
    }

    private fun generateTexture(render: SampleRender, placeInfo: Map<String, Any>): Texture {
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

    // Title text paint with larger size
    private val titlePaint = Paint().apply {
        textSize = 60f
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoBold ?: Typeface.DEFAULT_BOLD
    }

    // Detail text paint
    private val detailPaint = Paint().apply {
        textSize = 36f
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoRegular ?: Typeface.DEFAULT
    }

    // Type label paint
    private val typePaint = Paint().apply {
        textSize = 32f
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = robotoRegular ?: Typeface.DEFAULT
    }

    // Modern dark background with gradient
    private val backgroundPaint = Paint().apply {
        color = Color.argb(230, 40, 40, 50) // Dark blue-gray, slightly transparent
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Type badge background
    private val typeBadgePaint = Paint().apply {
        color = Color.argb(255, 71, 140, 237) // Bright blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Outer glow/shadow
    private val glowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    // Accent line
    private val accentLinePaint = Paint().apply {
        color = Color.argb(255, 71, 140, 237) // Bright blue
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private fun generateBitmapFromPlaceInfo(placeInfo: Map<String, Any>): Bitmap {
        Log.d(TAG, "Generating layer label for: ${placeInfo["name"]}")

        // Extract data from place info
        val name = placeInfo["name"] as? String ?: "Unknown Place"
        val type = placeInfo["type"] as? String ?: "unknown"
        val rating = placeInfo["rating"] as? Double ?: 0.0
        val phoneNumber = placeInfo["phone_number"] as? String ?: "No phone"

        // Fixed dimensions for this label style
        val width = 1000
        val height = 500

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(bitmap)

        // Draw outer glow/shadow
        val shadowRect = RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(shadowRect, 20f, 20f, glowPaint)

        // Draw background
        val bgRect = RectF(30f, 30f, width - 30f, height - 30f)
        canvas.drawRoundRect(bgRect, 16f, 16f, backgroundPaint)

        // Draw accent line at the top
        val accentRect = RectF(30f, 30f, width - 30f, 32f)
        canvas.drawRoundRect(accentRect, 2f, 2f, accentLinePaint)

        // Start position for text
        var yPosition = 110f

        // Draw place name (large)
        canvas.drawText(name, 60f, yPosition, titlePaint)
        yPosition += 90f

        // Draw type badge
        val typeText = type.capitalize()
        val typeBounds = Rect()
        typePaint.getTextBounds(typeText, 0, typeText.length, typeBounds)
        val badgeWidth = typeBounds.width() + 40f
        val badgeHeight = typeBounds.height() + 20f

        val badgeRect = RectF(60f, yPosition - badgeHeight + 10f, 60f + badgeWidth, yPosition + 10f)
        canvas.drawRoundRect(badgeRect, 12f, 12f, typeBadgePaint)

        // Draw type text on badge
        canvas.drawText(typeText, 80f, yPosition, typePaint)
        yPosition += 80f

        // Draw rating with star icon
        val ratingText = "%.1f".format(rating)
        starIcon?.let {
            canvas.drawBitmap(it, 60f, yPosition - 22f, null)
            canvas.drawText(ratingText, 95f, yPosition, detailPaint)
        } ?: canvas.drawText("★ $ratingText", 60f, yPosition, detailPaint)
        yPosition += 70f

        // Draw phone number with phone icon
        phoneIcon?.let {
            canvas.drawBitmap(it, 60f, yPosition - 22f, null)
            canvas.drawText(phoneNumber, 95f, yPosition, detailPaint)
        } ?: canvas.drawText("📞 $phoneNumber", 60f, yPosition, detailPaint)

        Log.d(TAG, "Created layer label bitmap: ${bitmap.width}x${bitmap.height}")
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

/**
 * Renderer for layer labels that manages drawing them in 3D space.
 */
class LayerLabelRenderer {
    companion object {
        private const val TAG = "LayerLabelRenderer"
    }

    private lateinit var context: Context
    private lateinit var cache: LayerLabelTextureCache
    private lateinit var mesh: com.example.explorelens.common.samplerender.Mesh
    private lateinit var shader: com.example.explorelens.common.samplerender.Shader

    // Constants for vertex buffer setup
    private val COORDS_BUFFER_SIZE = 2 * 4 * 4
    private val NDC_QUAD_COORDS_BUFFER = ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -2.5f, -1.25f, // Bottom left - wider aspect ratio for this label
                2.5f, -1.25f,  // Bottom right
                -2.5f, 1.25f,  // Top left
                2.5f, 1.25f,   // Top right
            ))
            position(0)
        }

    private val SQUARE_TEX_COORDS_BUFFER = ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f,
            ))
            position(0)
        }

    fun onSurfaceCreated(render: SampleRender, context: Context) {
        this.context = context

        // Initialize the cache
        cache = LayerLabelTextureCache(context)

        // Create shader with proper blending
        shader = com.example.explorelens.common.samplerender.Shader.createFromAssets(
            render,
            "shaders/layer_label.vert",
            "shaders/layer_label.frag",
            null
        )
            .setBlend(
                com.example.explorelens.common.samplerender.Shader.BlendFactor.SRC_ALPHA,
                com.example.explorelens.common.samplerender.Shader.BlendFactor.ONE_MINUS_SRC_ALPHA
            )
            .setDepthTest(false)
            .setDepthWrite(false)

        // Default color for the texture
        shader.setVec4("fragColor", floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f))

        // Create mesh from vertex buffers
        val vertexBuffers = arrayOf(
            com.example.explorelens.common.samplerender.VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
            com.example.explorelens.common.samplerender.VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
        )
        mesh = com.example.explorelens.common.samplerender.Mesh(
            render,
            com.example.explorelens.common.samplerender.Mesh.PrimitiveMode.TRIANGLE_STRIP,
            null,
            vertexBuffers
        )
    }

    // Position for the label
    private val labelOrigin = FloatArray(3)

    // Draw a layer label
    fun draw(
        render: SampleRender,
        viewProjectionMatrix: FloatArray,
        pose: Pose,
        cameraPose: Pose,
        placeInfo: Map<String, Any>
    ) {
        Log.d(TAG, "Drawing layer label for: ${placeInfo["name"]}")

        // Set the label origin from the pose
        // Position slightly higher for this label style
        labelOrigin[0] = pose.tx()
        labelOrigin[1] = pose.ty() + 0.2f // Higher position
        labelOrigin[2] = pose.tz()

        // Set shader uniforms
        shader
            .setMat4("u_ViewProjection", viewProjectionMatrix)
            .setVec3("u_LabelOrigin", labelOrigin)
            .setVec3("u_CameraPos", cameraPose.translation)
            .setTexture("uTexture", cache.get(render, placeInfo))

        // Draw the mesh
        render.draw(mesh, shader)

        Log.d(TAG, "Draw completed for layer label: ${placeInfo["name"]}")
    }
}