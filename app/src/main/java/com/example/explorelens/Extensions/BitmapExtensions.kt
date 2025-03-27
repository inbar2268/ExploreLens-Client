package com.example.explorelens.Extensions
import android.graphics.YuvImage
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.util.Log
import com.example.android.camera.utils.YuvToRgbConverter
import com.google.ar.core.ImageFormat
import java.io.File
import java.io.FileOutputStream

fun Bitmap.toFile(context: Context, name: String): File {
    val file = File(context.cacheDir, "image_$name.jpg")
    FileOutputStream(file).use { stream ->
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
    }
    Log.d("Snapshot", "Saved at: ${file.absolutePath}")
    return file
}

fun convertYuv(context: Context,image: Image): Bitmap {
    val yuvConverter = YuvToRgbConverter(context)
    return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
        yuvConverter.yuvToRgb(image, this)
    }
}


