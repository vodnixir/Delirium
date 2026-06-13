package dev.vodnixir.delirium.ui.camera

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Pulls the first frame of [videoFile] and compresses it to a JPEG. Used both as
 * the grid/widget still for a video post and as a quick local preview.
 */
fun extractVideoThumbnailJpeg(videoFile: File, maxLongSide: Int = 1080): ByteArray {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: error("Could not read a video frame")
        frame.toCompressedJpeg(maxLongSide)
    } finally {
        runCatching { retriever.release() }
    }
}
