package com.linkshield.sandbox.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/grabber/GrabberEngine.kt
// ← NEW FILE (does not exist in repo yet — create at this path)
//
// Production singleton for:
//  • YoutubeDL initialization + auto-update
//  • Media metadata fetch (title, thumbnail, available qualities)
//  • Download with FFmpeg mux for 1080p / 4K
//  • Progress tracking via StateFlow + cold Flow
//  • Error mapping (private/geo-blocked/timeout/429)
//
// UI freeze rule: BACKEND ONLY. No Compose imports allowed here.

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
    val label: String,           // "1080p", "720p", "MP3", "4K"
    val ext: String,             // "mp4", "mp3", "webm"
    val isAudioOnly: Boolean,
    val requiresMerge: Boolean   // true when height >= 1080 (needs FFmpeg mux)
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

    // ── Init — call from Application.onCreate() ──────────────────────────────
    // Must run synchronously on the main thread before any download or fetch.

    fun init(context: Context) {
        runCatching {
            YoutubeDL.getInstance().init(context)
            Log.i(TAG, "YoutubeDL initialized")
        }.onFailure {
            Log.e(TAG, "YoutubeDL init failed: ${it.message}")
        }
    }

    // ── Auto-update — keeps yt-dlp extractors fresh ──────────────────────────
    // Call from a background coroutine. Silent failure is fine (offline).

    suspend fun updateExtractor(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val status = YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                val msg = "Extractor update: $status"
                Log.i(TAG, msg)
                msg
            }.onFailure {
                Log.w(TAG, "Extractor update failed (OK if offline): ${it.message}")
            }
        }

    // ── Metadata fetch ────────────────────────────────────────────────────────

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

    // ── Download ──────────────────────────────────────────────────────────────
    // Returns a cold Flow — collect inside viewModelScope.launch { }.
    //
    // Usage:
    //   viewModelScope.launch {
    //       GrabberEngine.download(context, url, format, outputDir).collect { state ->
    //           _uiState.value = _uiState.value.copy(downloadState = state)
    //       }
    //   }

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
                            // Extract audio, convert to MP3
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")   // 0 = best VBR
                        }
                        format.requiresMerge -> {
                            // 1080p / 4K: best video stream + best audio, mux via FFmpeg
                            addOption("-f", "bestvideo[height<=${heightFor(format.label)}]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        else -> {
                            // 360p – 720p: single combined stream, no FFmpeg needed
                            addOption(
                                "-f",
                                "bestvideo[height<=${heightFor(format.label)}][ext=mp4]" +
                                "+bestaudio[ext=m4a]/best[height<=${heightFor(format.label)}]"
                            )
                        }
                    }

                    // Point yt-dlp to the bundled FFmpeg binary
                    addOption("--ffmpeg-location", getFFmpegPath(context))
                }

                var trackedFile: File? = null
                val processId = "linkshield_dl_${System.currentTimeMillis()}"

                YoutubeDL.getInstance().execute(
                    request,
                    processId = processId
                ) { progress, eta, line ->
                    val state = DownloadState.Progress(
                        percent = progress,
                        eta     = if (eta > 0) "${eta}s" else "",
                        speed   = extractSpeed(line)
                    )
                    _downloadState.value = state

                    // Track output file from yt-dlp progress lines
                    if (line.contains("[download] Destination:")) {
                        val path = line.substringAfter("[download] Destination:").trim()
                        trackedFile = File(path)
                    }
                }

                // Resolve output file
                trackedFile
                    ?: outputDir.listFiles()
                        ?.filter { it.isFile }
                        ?.maxByOrNull { it.lastModified() }
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

    // ── Cancel ────────────────────────────────────────────────────────────────

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        _downloadState.value = DownloadState.Idle
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildFormatList(info: VideoInfo): List<MediaFormat> {
        val result = mutableListOf<MediaFormat>()

        // Always offer MP3 (audio-only)
        result.add(
            MediaFormat(
                id            = "mp3",
                label         = "MP3",
                ext           = "mp3",
                isAudioOnly   = true,
                requiresMerge = false
            )
        )

        // Video formats: 360p → 4K, only offer what the source supports
        val availableHeights: Set<Int> = info.formats
            ?.mapNotNull { it.height?.toInt() }
            ?.toSet() ?: emptySet()

        val targetHeights = listOf(360, 480, 720, 1080, 2160)
        for (h in targetHeights) {
            // Offer quality if source has at least this height,
            // or if we can't determine available heights (offer all)
            if (availableHeights.isEmpty() || availableHeights.any { it >= h }) {
                val label = if (h == 2160) "4K" else "${h}p"
                result.add(
                    MediaFormat(
                        id            = "v$h",
                        label         = label,
                        ext           = "mp4",
                        isAudioOnly   = false,
                        requiresMerge = h >= 1080  // FFmpeg mux needed for 1080p+
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

    /** youtubedl-android ships FFmpeg inside the app's native library dir. */
    private fun getFFmpegPath(context: Context): String {
        val nativeDir  = context.applicationInfo.nativeLibraryDir
        val ffmpegFile = File(nativeDir, "libffmpeg.so")
        return if (ffmpegFile.exists()) ffmpegFile.absolutePath else nativeDir
    }

    /** Extract download speed from a yt-dlp progress log line. */
    private fun extractSpeed(line: String): String {
        // Example line: "[download]  12.3% of 45.00MiB at  2.10MiB/s ETA 00:19"
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
