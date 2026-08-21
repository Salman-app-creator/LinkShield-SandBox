package com.linkshield.sandbox.ui.grabber

// FIX: Package changed from com.linkshield.sandbox.grabber to com.linkshield.sandbox.ui.grabber
// to match actual file location.

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GrabberEngine"

// ── Data models ───────────────────────────────────────────────────────────────

data class MediaMetadata(
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val uploader: String,
    val formats: List<MediaFormat>
)

data class MediaFormat(
    val id: String,
    val label: String,
    val ext: String,
    val isAudioOnly: Boolean,
    val requiresMerge: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Fetching(val url: String) : DownloadState()
    data class Progress(val percent: Float, val eta: String, val speed: String) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ── Singleton ─────────────────────────────────────────────────────────────────

object GrabberEngine {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    fun init(context: Context) {
        runCatching {
            YoutubeDL.getInstance().init(context)
            Log.i(TAG, "YoutubeDL initialized")
        }.onFailure {
            Log.e(TAG, "YoutubeDL init failed: ${it.message}")
        }
    }

       suspend fun updateExtractor(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                // FIX: Version 0.14.0 mein sirf Context parameter chahiye
                // UpdateChannel enum exist nahi karta is version mein
                val status = YoutubeDL.getInstance().updateYoutubeDL(context)
                val msg = "Extractor update: $status"
                Log.i(TAG, msg)
                msg
            }.onFailure {
                Log.w(TAG, "Extractor update failed (OK if offline): ${it.message}")
            }
        }
        

    suspend fun fetchMetadata(url: String): Result<MediaMetadata> =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext Result.failure(
                IllegalArgumentException("URL must not be empty")
            )

            _downloadState.value = DownloadState.Fetching(url)

            runCatching {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--dump-json")
                    addOption("--no-playlist")
                    addOption("--socket-timeout", "20")
                }

                val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
                val formats = buildFormatList(info)

                MediaMetadata(
                    title           = info.title ?: "Unknown",
                    thumbnailUrl    = info.thumbnail,
                    durationSeconds = info.duration?.toLong() ?: 0L,
                    uploader        = info.uploader ?: "",
                    formats         = formats
                ).also {
                    _downloadState.value = DownloadState.Idle
                }
            }.onFailure { error ->
                val msg = mapError(error)
                _downloadState.value = DownloadState.Error(msg)
                Log.e(TAG, "Metadata fetch failed: $msg", error)
            }
        }

    fun download(
        context: Context,
        url: String,
        format: MediaFormat,
        outputDir: File
    ): Flow<DownloadState> = flow {
        if (url.isBlank()) {
            emit(DownloadState.Error("URL is empty"))
            return@flow
        }

        outputDir.mkdirs()
        emit(DownloadState.Progress(0f, "", ""))
        _downloadState.value = DownloadState.Progress(0f, "", "")

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--socket-timeout", "30")
                    addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")

                    when {
                        format.isAudioOnly -> {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        }
                        format.requiresMerge -> {
                            addOption("-f", "bestvideo[height<=${heightFor(format.label)}]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        else -> {
                            addOption(
                                "-f",
                                "bestvideo[height<=${heightFor(format.label)}][ext=mp4]" +
                                "+bestaudio[ext=m4a]/best[height<=${heightFor(format.label)}]"
                            )
                        }
                    }

                    addOption("--ffmpeg-location", getFFmpegPath(context))
                }

                var trackedFile: File? = null
                val processId = "linkshield_dl_${System.currentTimeMillis()}"

                YoutubeDL.getInstance().execute(
                    request,
                    processId
                ) { progress, eta, line ->
                    val state = DownloadState.Progress(
                        percent = progress,
                        eta     = if (eta > 0) "${eta}s" else "",
                        speed   = extractSpeed(line)
                    )
                    _downloadState.value = state

                    if (line.contains("[download] Destination:")) {
                        val path = line.substringAfter("[download] Destination:").trim()
                        trackedFile = java.io.File(path)
                    }
                }

                trackedFile
                    ?: outputDir.listFiles()
                        ?.filter { f -> f.isFile }
                        ?.maxByOrNull { f -> f.lastModified() }
                    ?: throw IllegalStateException("Downloaded file not found in $outputDir")
            }
        }

        result.fold(
            onSuccess = { file ->
                val success = DownloadState.Success(file)
                _downloadState.value = success
                emit(success)
            },
            onFailure = { error ->
                val msg = mapError(error)
                val errState = DownloadState.Error(msg)
                _downloadState.value = errState
                emit(errState)
                Log.e(TAG, "Download failed: $msg", error)
            }
        )
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        _downloadState.value = DownloadState.Idle
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    private fun buildFormatList(info: VideoInfo): List<MediaFormat> {
        val result = mutableListOf<MediaFormat>()

        result.add(
            MediaFormat(
                id            = "mp3",
                label         = "MP3",
                ext           = "mp3",
                isAudioOnly   = true,
                requiresMerge = false
            )
        )

        val availableHeights: Set<Int> = info.formats
            ?.mapNotNull { fmt -> fmt.height?.toInt() }
            ?.toSet() ?: emptySet()

        val targetHeights = listOf(360, 480, 720, 1080, 2160)
        for (h in targetHeights) {
            if (availableHeights.isEmpty() || availableHeights.any { height -> height >= h }) {
                val label = if (h == 2160) "4K" else "${h}p"
                result.add(
                    MediaFormat(
                        id            = "v$h",
                        label         = label,
                        ext           = "mp4",
                        isAudioOnly   = false,
                        requiresMerge = h >= 1080
                    )
                )
            }
        }

        return result
    }

    private fun heightFor(label: String): Int = when (label) {
        "4K"    -> 2160
        "1080p" -> 1080
        "720p"  -> 720
        "480p"  -> 480
        else    -> 360
    }

    private fun getFFmpegPath(context: Context): String {
        val nativeDir  = context.applicationInfo.nativeLibraryDir
        val ffmpegFile = java.io.File(nativeDir, "libffmpeg.so")
        return if (ffmpegFile.exists()) ffmpegFile.absolutePath else nativeDir
    }

    private fun extractSpeed(line: String): String {
        val regex = Regex("""at\s+([\d.]+\s*\w+/s)""")
        return regex.find(line)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun mapError(error: Throwable): String = when {
        error is java.net.UnknownHostException ->
            "No internet connection. Please check your network."
        error is java.net.SocketTimeoutException ->
            "Connection timed out. Try again."
        error.message?.contains("Private video", ignoreCase = true) == true ->
            "This video is private or requires login."
        error.message?.contains("not available", ignoreCase = true) == true ->
            "This video is not available in your region."
        error.message?.contains("429", ignoreCase = true) == true ->
            "Too many requests. Please wait a moment and try again."
        error.message?.contains("copyright", ignoreCase = true) == true ->
            "This video has been removed due to copyright."
        error.message?.contains("HTTP Error 403", ignoreCase = true) == true ->
            "Access denied. The platform is blocking the download."
        else ->
            error.message?.take(120) ?: "Download failed. Try a different URL."
    }
}
