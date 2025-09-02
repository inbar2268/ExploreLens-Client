
package com.example.explorelens.ar.render

import android.content.Context
import com.google.ar.core.Pose
import com.example.explorelens.common.samplerender.Mesh
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Shader
import com.example.explorelens.common.samplerender.VertexBuffer
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LabelRender {
  companion object {
    private const val TAG = "LabelRender"
    val COORDS_BUFFER_SIZE = 2 * 4 * 4

    // Default quad coordinates - these will be scaled in the vertex shader
    val NDC_QUAD_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            -1.2f, -0.6f, // Bottom left
            1.2f, -0.6f,  // Bottom right
            -1.2f, 0.6f,  // Top left
            1.2f, 0.6f,   // Top right
          )
        )
        position(0)
      }

    val SQUARE_TEX_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
          )
        )
        position(0)
      }

    // Default size of the label in screen space (can be adjusted)
    private const val DEFAULT_SCREEN_SIZE = 0.15f
  }

  // Store context for later use
  private lateinit var context: Context
  lateinit var cache: TextTextureCache

  lateinit var mesh: Mesh
  lateinit var shader: Shader

  // Size of label in screen space (proportion of screen height)
  private var screenSize = DEFAULT_SCREEN_SIZE

  fun onSurfaceCreated(render: SampleRender, context: Context) {
    this.context = context

    // Initialize cache with context for font loading
    cache = TextTextureCache(context)

    // Create shader with proper blending for transparency
    shader = Shader.createFromAssets(render, "shaders/billboard_label.vert", "shaders/label.frag", null)
      .setBlend(
        Shader.BlendFactor.SRC_ALPHA,
        Shader.BlendFactor.ONE_MINUS_SRC_ALPHA
      )
      .setDepthTest(false)
      .setDepthWrite(false)

    // Default pure white color for the texture (1.0 alpha for full opacity)
    shader.setVec4("fragColor", floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f))

    // Create mesh from vertex buffers
    val vertexBuffers = arrayOf(
      VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
      VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
    )
    mesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers)
  }

  val labelOrigin = FloatArray(3)

  fun draw(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    cameraPose: Pose,
    label: String
  ) {
    //Log.d(TAG, "Drawing label: $label")

    // Set the label origin from the pose
    // Adjust Y position slightly upward for better positioning
    labelOrigin[0] = pose.tx()
    labelOrigin[1] = pose.ty() + 0.1f  // Offset upward slightly
    labelOrigin[2] = pose.tz()

    // Set the shader uniforms
    shader
      .setMat4("u_ViewProjection", viewProjectionMatrix)
      .setVec3("u_LabelOrigin", labelOrigin)
      .setVec3("u_CameraPos", cameraPose.translation)
      .setTexture("uTexture", cache.get(render, label))
      .setFloat("u_ScreenSize", screenSize)

    // Draw the mesh
    render.draw(mesh, shader)

  }

  // Method to set the screen size (as a proportion of screen height)
  fun setScreenSize(size: Float) {
    screenSize = size
  }
}