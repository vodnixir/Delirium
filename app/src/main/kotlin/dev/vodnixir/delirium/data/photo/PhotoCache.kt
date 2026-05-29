package dev.vodnixir.delirium.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PhotoCache(context: Context) {
    private val dir = File(context.filesDir, "widget").apply { mkdirs() }

    private fun fileFor(connectionId: String) = File(dir, "$connectionId.jpg")

    fun pathFor(connectionId: String): String? {
        val file = fileFor(connectionId)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    suspend fun saveFromBytes(connectionId: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Could not decode photo bytes")
            val scaled = source.scaledForWidget()
            val file = fileFor(connectionId)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== source) scaled.recycle()
            source.recycle()
            file.absolutePath
        }

    fun delete(connectionId: String) {
        fileFor(connectionId).delete()
    }

    private fun Bitmap.scaledForWidget(): Bitmap {
        val longSide = maxOf(width, height)
        if (longSide <= WIDGET_MAX_LONG_SIDE) return this
        val scale = WIDGET_MAX_LONG_SIDE.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt(),
            (height * scale).toInt(),
            true,
        )
    }

    private companion object {
        const val WIDGET_MAX_LONG_SIDE = 1024
        const val JPEG_QUALITY = 85
    }
}
