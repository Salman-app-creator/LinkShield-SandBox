package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Single source of truth for media extraction and downloads.
 *
 * We intentionally use the bundled yt-dlp engine instead of the old Cobalt/Oracle
 * HTTP endpoint. That endpoint was receiving the WebView's search URL and returning
 * HTTP 400, and a captured googlevideo URL is not a reliable long-lived download URL.
 */
object GrabberEngine {

    private const val TAG = "GrabberEngine"

    @Volatile
    private var initialized = false

    data class MediaInfoResult(
        val success: Boolean,
        val sourceUrl: String,
        val playableUrl: String? = null,
        val title: String = "",
        val thumbnail: String = "",
        val extension: String = "mp4",
        val mimeType: String = "video/mp4",
        val error: String? = null
    )

    data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null
    )

    fun init(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        YoutubeDL.getInstance().init(appContext)
        // FFmpeg is required when yt-dlp has to merge video+audio or convert to MP3.
        FFmpeg.getInstance().init(appContext)
        initialized = true
        Log.d(TAG, "GrabberEngine + FFmpeg initialized")
    }

    fun updateExtractor(context: Context) {
        val result = YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext)
        Log.d(TAG, "Extractor update result: $result")
    }

    fun isReady(): Boolean = initialized

    /**
     * Extract metadata and a playable preview URL using yt-dlp.
     * No download is performed here.
     */
    suspend fun fetchMediaInfo(
        context: Context,
        pageUrl: String,
        resolution: String,
        audioOnly: Boolean
    ): MediaInfoResult = withContext(Dispatchers.IO) {
        val source = normalizeSourceUrl(pageUrl)
        if (source.isBlank()) {
            return@withContext MediaInfoResult(false, sourceUrl = source, error = "Enter a valid media URL")
        }

        try {
            ensureReady(context)

            val height = resolutionToHeight(resolution)
            val request = YoutubeDLRequest(source)

            if (audioOnly) {
                request.addOption("-f", "bestaudio/best")
            } else {
                request.addOption("-f", bestPreviewFormat(height))
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val title = info.getTitle().orEmpty().ifBlank { "LinkShield Media" }
            val thumbnail = info.getThumbnail().orEmpty()
            val playableUrl = info.getUrl().orEmpty()
            val ext = if (audioOnly) "mp3" else "mp4"
            val mime = if (audioOnly) "audio/mpeg" else "video/mp4"

            if (playableUrl.isBlank()) {
                MediaInfoResult(
                    success = false,
                    sourceUrl = source,
                    title = title,
                    thumbnail = thumbnail,
                    error = "yt-dlp parsed the page but returned no playable stream"
                )
            } else {
                MediaInfoResult(
                    success = true,
                    sourceUrl = source,
                    playableUrl = playableUrl,
                    title = title,
                    thumbnail = thumbnail,
                    extension = ext,
                    mimeType = mime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media extraction failed for $source", e)
            MediaInfoResult(
                success = false,
                sourceUrl = source,
                error = friendlyError(e)
            )
        }
    }

    /**
     * Download directly with yt-dlp so separate YouTube video/audio streams are
     * merged by FFmpeg and the result is a real file in Downloads/LinkShield.
     */
    suspend fun downloadMedia(
        context: Context,
        pageUrl: String,
        resolution: String,
        audioOnly: Boolean,
        onProgress: (Float) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val source = normalizeSourceUrl(pageUrl)
        if (source.isBlank()) {
            return@withContext DownloadResult(false, error = "Media URL is empty")
        }

        try {
            ensureReady(context)

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LinkShield"
            )
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                return@withContext DownloadResult(false, error = "Could not create Downloads/LinkShield")
            }

            val height = resolutionToHeight(resolution)
            val request = YoutubeDLRequest(source)
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--no-playlist")
            request.addOption("-o", File(downloadDir, "%(title)s.%(ext)s").absolutePath)

            if (audioOnly) {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            } else {
                request.addOption("-f", bestDownloadFormat(height))
                request.addOption("--merge-output-format", "mp4")
            }

            val response = YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress.coerceIn(0f, 100f))
            }

            if (response.exitCode != 0) {
                return@withContext DownloadResult(
                    false,
                    error = cleanProcessError(
                        response.err.orEmpty().ifBlank { response.out.orEmpty() }
                    )
                )
            }

            val outputFile = findNewestCompletedFile(downloadDir, audioOnly)
            if (outputFile == null) {
                DownloadResult(false, error = "Download finished but the output file was not found")
            } else {
                onProgress(100f)
                DownloadResult(true, file = outputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media download failed for $source", e)
            DownloadResult(false, error = friendlyError(e))
        }
    }

    private fun ensureReady(context: Context) {
        if (!initialized) init(context)
    }

    private fun normalizeSourceUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("about:blank", true)) return ""

        return try {
            val uri = android.net.Uri.parse(trimmed)
            val host = uri.host?.lowercase(Locale.US).orEmpty()
            when {
                host == "youtu.be" -> {
                    val id = uri.path?.trim('/')?.substringBefore('/')
                    if (!id.isNullOrBlank()) "https://www.youtube.com/watch?v=$id" else trimmed
                }
                host.endsWith("youtube.com") && uri.path.equals("/watch", true) -> {
                    val id = uri.getQueryParameter("v")
                    if (!id.isNullOrBlank()) "https://www.youtube.com/watch?v=$id" else trimmed
                }
                host.endsWith("youtube.com") && uri.path?.startsWith("/shorts/") == true -> {
                    val id = uri.path!!.substringAfter("/shorts/").substringBefore('/')
                    if (id.isNotBlank()) "https://www.youtube.com/watch?v=$id" else trimmed
                }
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun resolutionToHeight(resolution: String): Int = when (resolution.uppercase(Locale.US)) {
        "4K" -> 2160
        "1080P" -> 1080
        "720P" -> 720
        "480P" -> 480
        "360P" -> 360
        else -> 1080
    }

    private fun bestPreviewFormat(height: Int): String =
        "best[height<=$height]/best"

    private fun bestDownloadFormat(height: Int): String =
        "bestvideo[height<=$height][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"

    private fun findNewestCompletedFile(dir: File, audioOnly: Boolean): File? {
        val allowed = if (audioOnly) {
            setOf("mp3")
        } else {
            setOf("mp4", "mkv", "webm", "mov")
        }

        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase(Locale.US) in allowed }
            ?.filterNot { it.name.endsWith(".part", true) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun cleanProcessError(raw: String): String {
        val cleaned = raw
            .lineSequence()
            .filter { line ->
                val value = line.trim()
                value.isNotBlank() &&
                    !value.startsWith("[debug]", true) &&
                    !value.startsWith("[youtube]", true) &&
                    !value.startsWith("[info]", true)
            }
            .joinToString(" ")
            .trim()
        return cleaned.take(500).ifBlank { "yt-dlp download failed" }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("not initialized", true) -> "Grabber engine is still starting. Please try again."
            message.contains("Unsupported URL", true) -> "This website/link is not supported by the current yt-dlp extractor."
            message.contains("Sign in", true) -> "This media requires sign-in or is age/region restricted."
            message.isNotBlank() -> message.take(500)
            else -> "Media extraction failed"
        }
    }
}
