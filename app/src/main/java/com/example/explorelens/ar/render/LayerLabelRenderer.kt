package com.example.explorelens.ar.render

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.example.explorelens.common.samplerender.Mesh
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Shader
import com.example.explorelens.common.samplerender.VertexBuffer
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renderer for layer labels that manages drawing them in 3D space.
 */
class LayerLabelRenderer {
    companion object {
        private const val TAG = "LayerLabelRenderer"
    }

    private lateinit var context: Context
    private lateinit var cache: LayerLabelTextureCache
    private lateinit var mesh: Mesh
    private lateinit var shader: Shader

    // Constants for vertex buffer setup
    private val COORDS_BUFFER_SIZE = 2 * 4 * 4

    // Updated buffer with adjusted ratio for narrower label width
    private val NDC_QUAD_COORDS_BUFFER = ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(
                -2.0f, -1.5f, // Bottom left - adjusted for narrower width
                2.0f, -1.5f,  // Bottom right
                -2.0f, 1.5f,  // Top left
                2.0f, 1.5f,   // Top right
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
        shader = Shader.createFromAssets(
            render,
            "shaders/layer_label.vert",
            "shaders/layer_label.frag",
            null
        )
            .setBlend(
                Shader.BlendFactor.SRC_ALPHA,
                Shader.BlendFactor.ONE_MINUS_SRC_ALPHA
            )
            .setDepthTest(false)
            .setDepthWrite(false)

        // Default color for the texture
        shader.setVec4("fragColor", floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f))

        // Create mesh from vertex buffers
        val vertexBuffers = arrayOf(
            VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
            VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
        )
        mesh = Mesh(
            render,
            Mesh.PrimitiveMode.TRIANGLE_STRIP,
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