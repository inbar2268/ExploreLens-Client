package com.example.explorelens.ml.render

import com.google.ar.core.Pose
import com.example.explorelens.common.samplerender.Mesh
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.common.samplerender.Shader
import com.example.explorelens.common.samplerender.VertexBuffer
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log

class LabelRender {
  companion object {
    private const val TAG = "LabelRender"
    val COORDS_BUFFER_SIZE = 2 * 4 * 4

    val NDC_QUAD_COORDS_BUFFER =
      ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(
        ByteOrder.nativeOrder()
      ).asFloatBuffer().apply {
        put(
          floatArrayOf(
            -1.5f, -1.5f,
            1.5f, -1.5f,
            -1.5f, 1.5f,
            1.5f, 1.5f,
          )
        )
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
      }
  }

  val cache = TextTextureCache()

  lateinit var mesh: Mesh
  lateinit var shader: Shader
  lateinit var containerShader: Shader
  lateinit var containerMesh: Mesh

  val labelPaint = Paint().apply {
    color = Color.WHITE
    textSize = 40f
    isAntiAlias = true
    typeface = Typeface.DEFAULT_BOLD
  }

  val containerPaint = Paint().apply {
    color = Color.argb(128, 0, 0, 0)  // Semi-transparent black container
    isAntiAlias = true
  }

  fun onSurfaceCreated(render: SampleRender) {
    shader = Shader.createFromAssets(render, "shaders/label.vert", "shaders/label.frag", null)
      .setBlend(
        Shader.BlendFactor.ONE, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA
      )
      .setDepthTest(false)
      .setDepthWrite(false)
    shader.setVec4("fragColor", floatArrayOf(0f, 0f, 0f, 0.5f)) // Semi-transparent black color

    containerShader = Shader.createFromAssets(render, "shaders/label.vert", "shaders/label.frag", null)
      .setBlend(
        Shader.BlendFactor.ONE, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA
      )
      .setDepthTest(false)
      .setDepthWrite(false)

    val vertexBuffers = arrayOf(
      VertexBuffer(render, 2, NDC_QUAD_COORDS_BUFFER),
      VertexBuffer(render, 2, SQUARE_TEX_COORDS_BUFFER),
    )
    mesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers)
    val containerVertexBuffers = arrayOf(
      VertexBuffer(render, 2, ByteBuffer.allocateDirect(0).asFloatBuffer())
    )
    containerMesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, containerVertexBuffers)

  }

  val labelOrigin = FloatArray(3)

  fun draw(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    cameraPose: Pose,
    label: String
  ) {
    Log.d(TAG, "Drawing label: $label")
    labelOrigin[0] = pose.tx()
    labelOrigin[1] = pose.ty()
    labelOrigin[2] = pose.tz()

    val labelWidth = labelPaint.measureText(label)
    val labelHeight = labelPaint.textSize
    val padding = 20f  // Padding for the container

    // Calculate the container's width and height
    val containerWidth = labelWidth + 2 * padding
    val containerHeight = labelHeight + 2 * padding
    val containerX = labelOrigin[0] - containerWidth / 2
    val containerY = labelOrigin[1] - containerHeight / 2
    Log.d(TAG, "Container dimensions: $containerWidth x $containerHeight")

    // Create a Pose for the container (use 0f for Z position if it's on the same plane as the label)
    val containerPose = Pose.makeTranslation(containerX, containerY, 0f)

    // Draw background container (a rectangle)
    drawContainer(render, viewProjectionMatrix, containerPose, containerWidth, containerHeight)


    // Draw the label text on top of the container
    shader
      .setMat4("u_ViewProjection", viewProjectionMatrix)
      .setVec3("u_LabelOrigin", labelOrigin)
      .setVec3("u_CameraPos", cameraPose.translation)
      .setTexture("uTexture", cache.get(render, label))
    render.draw(mesh, shader)
    Log.d(TAG, "Draw completed for label: $label")
  }

  fun drawContainer(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    pose: Pose,
    containerWidth: Float,
    containerHeight: Float
  ) {
    // Define corner radius (optional)
    val cornerRadius = 0.1f  // Optional: can adjust for rounded corners

    // Create a mesh for the container with updated dimensions
    val meshCoords = createRectangleMesh(0f, 0f, containerWidth, containerHeight, cornerRadius)

    // Debugging: Print out the coordinates for the container mesh
    Log.d(TAG, "Container mesh coordinates: ${meshCoords.joinToString(", ")}")

    // Create a new ByteBuffer with the mesh coordinates
    val meshBuffer = ByteBuffer.allocateDirect(meshCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    meshBuffer.put(meshCoords)

    // Create the new containerMesh with updated vertex buffer
    val containerMesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, arrayOf(VertexBuffer(render, 2, meshBuffer)))

    // Set the shader color to semi-transparent black (for the container)
    containerShader.setVec4("fragColor", floatArrayOf(0f, 0f, 0f, 1.0f)) // RGBA for semi-transparent black

    // Set the view projection matrix
    containerShader.setMat4("u_ViewProjection", viewProjectionMatrix)

    // Disable depth testing and writing to ensure the container is drawn on top of other elements
    containerShader.setDepthTest(false)
    containerShader.setDepthWrite(false)

    // Debugging: Confirming depth test is disabled
    Log.d(TAG, "Disabling depth test for Container")

    // Draw the container mesh
    render.draw(containerMesh, containerShader)

    // Debugging: Confirm if the container is being drawn
    Log.d(TAG, "Container drawing completed")
  }

  private fun createRectangleMesh(x: Float, y: Float, width: Float, height: Float, cornerRadius: Float): FloatArray {
    val coords = mutableListOf<Float>()

    // Convert world coordinates to NDC space by normalizing them
    val left = (x - width / 2) / 500f  // Scale factor to fit in NDC space
    val right = (x + width / 2) / 500f
    val top = (y - height / 2) / 500f
    val bottom = (y + height / 2) / 500f

    // Debugging: Log adjusted values for NDC
    Log.d(TAG, "Adjusted container coordinates (NDC): left=$left, right=$right, top=$top, bottom=$bottom")

    // Simple rectangle without rounded corners (adjusted for NDC)
    coords.add(left)  // Left
    coords.add(top)   // Top

    coords.add(right)  // Right
    coords.add(top)    // Top

    coords.add(left)  // Left
    coords.add(bottom) // Bottom

    coords.add(right)  // Right
    coords.add(bottom) // Bottom

    // Return the array of coordinates
    return coords.toFloatArray()
  }
  fun drawContainerSimple(
    render: SampleRender,
    viewProjectionMatrix: FloatArray,
    containerWidth: Float,
    containerHeight: Float
  ) {
    // Use known NDC coordinates to position the container in the center of the screen
    val centerX = 0f
    val centerY = 0f

    // Define the mesh coordinates for the container (using simple square geometry)
    val meshCoords = floatArrayOf(
      -containerWidth / 2, -containerHeight / 2,  // Bottom-left corner
      containerWidth / 2, -containerHeight / 2,   // Bottom-right corner
      -containerWidth / 2, containerHeight / 2,   // Top-left corner
      containerWidth / 2, containerHeight / 2     // Top-right corner
    )

    // Create a buffer for the mesh coordinates
    val meshBuffer = ByteBuffer.allocateDirect(meshCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    meshBuffer.put(meshCoords)

    // Create a simple mesh for the container
    val containerMesh = Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, arrayOf(VertexBuffer(render, 2, meshBuffer)))

    // Create a basic shader for the container (use a solid color)
    containerShader.setVec4("fragColor", floatArrayOf(0f, 0f, 1f, 1f))  // Solid blue for visibility

    // Set the view-projection matrix to render the container
    containerShader.setMat4("u_ViewProjection", viewProjectionMatrix)

    // Disable depth test so the container renders above other objects
    containerShader.setDepthTest(false)
    containerShader.setDepthWrite(false)

    // Draw the container mesh
    render.draw(containerMesh, containerShader)

    Log.d(TAG, "Mesh Coordinates: ${meshCoords.joinToString()}")
    Log.d(TAG, "Rendering Container at: (0,0) with dimensions: $containerWidth x $containerHeight")

    // Log to confirm the drawing process
    Log.d(TAG, "Simple container drawing completed")
  }

}