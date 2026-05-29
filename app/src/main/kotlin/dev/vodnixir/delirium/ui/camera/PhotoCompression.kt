package dev.vodnixir.delirium.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_LONG_SIDE_PX = 1080
private const val JPEG_QUALITY = 80

fun ImageProxy.toCompressedJpeg(): ByteArray {
    val buffer = planes[0].buffer
    val rawBytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rotation = imageInfo.rotationDegrees
    val source = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        ?: error("Failed to decode captured image")
    val oriented = if (rotation != 0) source.rotated(rotation.toFloat()) else source
    val scaled = oriented.scaledToFit(MAX_LONG_SIDE_PX)
    val bytes = scaled.toJpegBytes()
    if (scaled !== source) scaled.recycle()
    if (oriented !== source) oriented.recycle()
    source.recycle()
    return bytes
}

/** Decodes a gallery image [uri], fixes EXIF rotation, downscales and compresses to JPEG. */
suspend fun compressUriToJpeg(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not open image")

        val sampleSize = computeSampleSize(maxOf(bounds.outWidth, bounds.outHeight), MAX_LONG_SIDE_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: error("Could not decode image")

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
        } ?: 0
        val oriented = if (rotation != 0) decoded.rotated(rotation.toFloat()) else decoded
        val scaled = oriented.scaledToFit(MAX_LONG_SIDE_PX)
        val bytes = scaled.toJpegBytes()
        if (scaled !== decoded) scaled.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        bytes
    }

/** Compresses an in-memory bitmap (e.g. a drawing) to JPEG. */
fun Bitmap.toCompressedJpeg(maxLongSide: Int = MAX_LONG_SIDE_PX): ByteArray {
    val scaled = scaledToFit(maxLongSide)
    val bytes = scaled.toJpegBytes()
    if (scaled !== this) scaled.recycle()
    return bytes
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    return out.toByteArray()
}

private fun computeSampleSize(longSide: Int, target: Int): Int {
    var sample = 1
    while (longSide / sample > target * 2) sample *= 2
    return sample
}

private fun Bitmap.rotated(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.scaledToFit(maxLongSide: Int): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= maxLongSide) return this
    val scale = maxLongSide.toFloat() / longSide
    val newW = (width * scale).toInt().coerceAtLeast(1)
    val newH = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newW, newH, true)
}
