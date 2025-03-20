
package com.example.explorelens.ml.classification

import android.app.Activity
import android.media.Image
import com.example.explorelens.ml.classification.utils.ImageUtils
import com.example.explorelens.ml.classification.utils.VertexUtils.rotateCoordinates
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.asDeferred

/**
 * Analyzes an image using ML Kit.
 */
class MLKitObjectDetector(context: Activity) : ObjectDetector(context) {
  // To use a custom model, follow steps on https://developers.google.com/ml-kit/vision/object-detection/custom-models/android.
   //val model = LocalModel.Builder().setAssetFilePath("inception_v4_1_metadata_1.tflite").build()
//   val builder = CustomObjectDetectorOptions.Builder(model)

  // For the ML Kit default model, use the following:
  val builder = ObjectDetectorOptions.Builder()

  private val options = builder
    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
    .enableClassification()
    .enableMultipleObjects()
    .build()
  private val detector = ObjectDetection.getClient(options)

  override suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult> {
    // `image` is in YUV (https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireCameraImage()),
    val convertYuv = convertYuv(image)

    // The model performs best on upright images, so rotate it.
    val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)

    val inputImage = InputImage.fromBitmap(rotatedImage, 0)

    val mockResults = listOf(
      DetectedObjectResult(
        confidence = 0.98f,
        label = "Eiffel Tower",
        centerCoordinate = Pair(300, 700)  // Mock coordinates
      ),
      DetectedObjectResult(
        confidence = 0.87f,
        label = "London eye",
        centerCoordinate = Pair(200, 500)  // Mock coordinates
      )
    )
    return mockResults


//    val mlKitDetectedObjects = detector.process(inputImage).asDeferred().await()
//    return mlKitDetectedObjects.mapNotNull { obj ->
//      val bestLabel = obj.labels.maxByOrNull { label -> label.confidence } ?: return@mapNotNull null
//      val coords = obj.boundingBox.exactCenterX().toInt() to obj.boundingBox.exactCenterY().toInt()
//      val rotatedCoordinates = coords.rotateCoordinates(rotatedImage.width, rotatedImage.height, imageRotation)
//      DetectedObjectResult(bestLabel.confidence, bestLabel.text, rotatedCoordinates)
//    }
  }

  @Suppress("USELESS_IS_CHECK")
  fun hasCustomModel() = builder is CustomObjectDetectorOptions.Builder
}