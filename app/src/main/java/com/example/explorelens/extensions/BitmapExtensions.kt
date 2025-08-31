package com.example.explorelens.extensions
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import com.example.explorelens.extensions.YuvToRgbConverter
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


