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

object GrabberEngine {

    suspend fun fetchMediaInfo(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false
    ): GrabberInfoResult = withContext(Dispatchers.IO) {
        // Fix: Removed 'context' parameter from MediaExtractorRepository constructor
        val repo = MediaExtractorRepository()
        val list = repo.extract(mediaUrl = pageUrl, title = "")
        
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
                thumbnail = "",
                error = null
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
                error = "Unable to fetch video from Cobalt server."
            )
        }
    }

    suspend fun downloadMedia(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false,
        onProgress: (Float) -> Unit
    ): GrabberDownloadResult = withContext(Dispatchers.IO) {
        // Direct handling shifted to Android DownloadManager
        GrabberDownloadResult(success = true)
    }
}
