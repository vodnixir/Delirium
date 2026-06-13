package dev.vodnixir.delirium.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Offline send queue. A captured photo is written to disk and an upload is
 * scheduled with a CONNECTED constraint, so it leaves immediately when online and
 * waits for connectivity otherwise. Each item is its own unique [SendPhotoWorker]
 * keyed by id, which keeps concurrent sends from racing over a shared drain.
 */
class OutboxRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, OUTBOX_DIR).apply { mkdirs() }

    @Serializable
    data class Meta(
        val id: String,
        val connectionId: String,
        val senderId: String,
        val caption: String? = null,
        val createdAt: Long,
        /** "image" or "video". */
        val mediaType: String = MEDIA_IMAGE,
    )

    /** A queued send. [media] is the jpg (image) or mp4 (video); [thumb] is set for video. */
    data class Pending(val meta: Meta, val media: File, val thumb: File?)

    /** Persists [jpegBytes] and schedules its upload (now if online, later otherwise). */
    fun enqueue(
        connectionId: String,
        senderId: String,
        jpegBytes: ByteArray,
        caption: String? = null,
    ) {
        val id = UUID.randomUUID().toString()
        File(dir, "$id.jpg").writeBytes(jpegBytes)
        writeMetaAndSchedule(id, connectionId, senderId, caption, MEDIA_IMAGE)
    }

    /** Persists a recorded clip plus its still thumbnail and schedules the upload. */
    fun enqueueVideo(
        connectionId: String,
        senderId: String,
        mp4Bytes: ByteArray,
        thumbnailJpeg: ByteArray,
        caption: String? = null,
    ) {
        val id = UUID.randomUUID().toString()
        File(dir, "$id.mp4").writeBytes(mp4Bytes)
        File(dir, "$id.thumb.jpg").writeBytes(thumbnailJpeg)
        writeMetaAndSchedule(id, connectionId, senderId, caption, MEDIA_VIDEO)
    }

    private fun writeMetaAndSchedule(
        id: String,
        connectionId: String,
        senderId: String,
        caption: String?,
        mediaType: String,
    ) {
        val meta = Meta(
            id = id,
            connectionId = connectionId,
            senderId = senderId,
            caption = caption?.takeIf { it.isNotBlank() },
            createdAt = System.currentTimeMillis(),
            mediaType = mediaType,
        )
        File(dir, "$id.json").writeText(json.encodeToString(meta))
        scheduleUpload(id)
    }

    fun load(id: String): Pending? {
        val metaFile = File(dir, "$id.json")
        if (!metaFile.exists()) return null
        val meta = runCatching { json.decodeFromString<Meta>(metaFile.readText()) }.getOrNull()
            ?: return null
        return if (meta.mediaType == MEDIA_VIDEO) {
            val media = File(dir, "$id.mp4")
            val thumb = File(dir, "$id.thumb.jpg")
            if (!media.exists() || !thumb.exists()) null else Pending(meta, media, thumb)
        } else {
            val media = File(dir, "$id.jpg")
            if (!media.exists()) null else Pending(meta, media, null)
        }
    }

    fun remove(id: String) {
        File(dir, "$id.json").delete()
        File(dir, "$id.jpg").delete()
        File(dir, "$id.mp4").delete()
        File(dir, "$id.thumb.jpg").delete()
    }

    /** Re-schedules upload work for anything still on disk (e.g. after a reboot). */
    fun resyncPending() {
        dir.listFiles { f -> f.extension == "json" }
            ?.forEach { scheduleUpload(it.nameWithoutExtension) }
    }

    private fun scheduleUpload(id: String) {
        val request = OneTimeWorkRequestBuilder<SendPhotoWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SendPhotoWorker.KEY_ID to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_PREFIX + id,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val OUTBOX_DIR = "outbox"
        const val WORK_PREFIX = "delirium_outbox_"
        const val MEDIA_IMAGE = "image"
        const val MEDIA_VIDEO = "video"
        val json = Json { ignoreUnknownKeys = true }
    }
}
