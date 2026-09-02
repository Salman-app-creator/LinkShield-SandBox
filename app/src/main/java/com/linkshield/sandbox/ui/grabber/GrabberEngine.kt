package com.linkshield.sandbox.ui.grabber

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GrabberInfoResult(
    val success: Boolean,
    val sourceUrl: String,
    val playableUrl: String?,
    val title: String,
    val extension: String,
    val mimeType: String,
    val thumbnail: String,
    val error: String? = null
)

data class GrabberDownloadResult(
    val success: Boolean,
    val error: String? = null
)

/**
 * Backend facade for the grabber. UI is intentionally not modified.
 * DownloadManager remains responsible for saving the resolved URL to Downloads.
 */
object GrabberEngine {

    suspend fun fetchMediaInfo(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false
    ): GrabberInfoResult = withContext(Dispatchers.IO) {
        val repo = MediaExtractorRepository(context.applicationContext)
        val list = repo.extract(mediaUrl = pageUrl, title = "", audioOnly = audioOnly, resolution = resolution)

        if (list.isNotEmpty()) {
            val item = list.first()
            val ext = if (audioOnly || item.isAudio) "mp3" else "mp4"
            GrabberInfoResult(
                success = true,
                sourceUrl = pageUrl,
                playableUrl = item.url,
                title = item.title,
                extension = ext,
                mimeType = item.mimeType,
                thumbnail = ""
            )
        } else {
            GrabberInfoResult(
                success = false,
                sourceUrl = pageUrl,
                playableUrl = null,
                title = "",
                extension = "mp4",
                mimeType = "video/mp4",
                thumbnail = "",
                error = "Unable to fetch media. Check the Cobalt instance and source URL."
            )
        }
    }

    /**
     * Kept as a real backend method instead of the previous unconditional
     * success=true stub. The current locked UI uses DownloadManager directly,
     * so this method is not used by the UI path yet.
     */
    suspend fun downloadMedia(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false,
        onProgress: (Float) -> Unit
    ): GrabberDownloadResult = withContext(Dispatchers.IO) {
        val repo = MediaExtractorRepository(context.applicationContext)
        val options = repo.extract(pageUrl, audioOnly = audioOnly, resolution = resolution)
        val item = options.firstOrNull() ?: return@withContext GrabberDownloadResult(false, "Unable to resolve media.")
        MediaDownloadEngine.download(
            context = context.applicationContext,
            mediaUrl = item.url,
            filename = item.title.ifBlank { "LinkShield_Media.${if (audioOnly) "mp3" else "mp4"} " }.trim(),
            mimeType = item.mimeType.ifBlank { if (audioOnly) "audio/mpeg" else "video/mp4" },
            onProgress = onProgress
        )
    }
}
