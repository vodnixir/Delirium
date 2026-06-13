package dev.vodnixir.delirium.data.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.vodnixir.delirium.DeliriumApplication

/**
 * Uploads one queued photo from the [OutboxRepository]. Removes it on success;
 * retries (with backoff) when the upload fails so an offline send eventually
 * lands once connectivity returns. A missing item means it was already sent.
 */
class SendPhotoWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val container = (applicationContext as DeliriumApplication).container
        val pending = container.outboxRepository.load(id) ?: return Result.success()
        val photoRepository = container.photoRepository
        return runCatching {
            val thumb = pending.thumb
            if (thumb != null) {
                photoRepository.uploadVideo(
                    connectionId = pending.meta.connectionId,
                    senderId = pending.meta.senderId,
                    mp4Bytes = pending.media.readBytes(),
                    thumbnailJpeg = thumb.readBytes(),
                    caption = pending.meta.caption,
                )
            } else {
                photoRepository.uploadPhoto(
                    connectionId = pending.meta.connectionId,
                    senderId = pending.meta.senderId,
                    jpegBytes = pending.media.readBytes(),
                    caption = pending.meta.caption,
                )
            }
        }.fold(
            onSuccess = {
                container.outboxRepository.remove(id)
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_ID = "outbox_id"
    }
}
