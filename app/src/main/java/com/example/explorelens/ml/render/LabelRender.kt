
package com.example.explorelens.ml.render

import android.util.Log
import com.google.ar.core.Pose
import com.example.explorelens.common.samplerender.Mesh
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Shader
import com.example.explorelens.common.samplerender.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws a label. See [draw].
 */
class LabelRender {
  companion object {
    private const val TAG = "LabelRender"
    val COORDS_BUFFER_SIZE = 2 * 4 * 4

    /**
     * Vertex buffer data for the mesh quad.
     */
    val NDC_QUAD_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            /*0:*/
            -1.5f, -1.5f,
            /*1:*/
            1.5f, -1.5f,
            /*2:*/
            -1.5f, 1.5f,
            /*3:*/
            1.5f, 1.5f,
          )
        )
      }

    /**
     * Vertex buffer data for texture coordinates.
     */
    val SQUARE_TEX_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            /*0:*/
            0f, 0f,
            /*1:*/
            1f, 0f,
            /*2:*/
            0f, 1f,
            /*3:*/
            1f, 1f,
          )
        )
      }
  }

  val cache = TextTextureCache()

  lateinit var mesh: Mesh
  lateinit var shader: Shader

  fun onSurfaceCreated(render: SampleRender) {
    shader = Shader.createFromAssets(render, "shaders/label.vert", "shaders/label.frag", null)
      .setBlend(
        Shader.BlendFactor.ONE, // ALPHA (src)
        Shader.BlendFactor.ONE_MINUS_SRC_ALPHA // ALPHA (dest)
      )
      .setDepthTest(false)
      .setDepthWrite(false)

    val vertexBuffers = arrayOf(
      VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
      VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
    )
    mesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers)
    Log.d(TAG, "LabelRender surface created")
  }

  val labelOrigin = FloatArray(3)

  /**
   * Draws a label quad with text [label] at [pose]. The label will rotate to face [cameraPose] around the Y-axis.
   */
  fun draw(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    cameraPose: Pose,
    label: String
  ) {
    // Get or create texture
    val texture = cache.get(render, label)

    if (texture == null) {
      Log.e(TAG, "CRITICAL: No texture generated for label: $label")
      return
    }

    // Calculate face direction (billboard effect)
    val lookAt = FloatArray(3)
    val up = floatArrayOf(0f, 1f, 0f)
    val forward = FloatArray(3)

    // Vector from camera to label
    forward[0] = pose.tx() - cameraPose.tx()
    forward[1] = pose.ty() - cameraPose.ty()
    forward[2] = pose.tz() - cameraPose.tz()

    // Normalize the forward vector
    val length = Math.sqrt((forward[0]*forward[0] + forward[1]*forward[1] + forward[2]*forward[2]).toDouble()).toFloat()
    forward[0] /= length
    forward[1] /= length
    forward[2] /= length

    // Adjust label origin to be closer to camera
    val labelOrigin = FloatArray(3)
    val scaleFactor = 0.5f  // Adjust this to control label distance
    labelOrigin[0] = cameraPose.tx() + (forward[0] * scaleFactor)
    labelOrigin[1] = cameraPose.ty() + (forward[1] * scaleFactor)
    labelOrigin[2] = cameraPose.tz() + (forward[2] * scaleFactor)

    // Log adjusted label position
    Log.d(TAG, "Adjusted Label Origin:")
    Log.d(TAG, "  X: ${labelOrigin[0]}")
    Log.d(TAG, "  Y: ${labelOrigin[1]}")
    Log.d(TAG, "  Z: ${labelOrigin[2]}")

    shader
      .setMat4("u_ViewProjection", viewProjectionMatrix)
      .setVec3("u_LabelOrigin", labelOrigin)
      .setVec3("u_CameraPos", cameraPose.translation)
      .setTexture("uTexture", texture)

    render.draw(mesh, shader)
  }
}