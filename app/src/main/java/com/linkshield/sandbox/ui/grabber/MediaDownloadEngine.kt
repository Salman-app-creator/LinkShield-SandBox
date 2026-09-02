package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Phase 3 local downloader.
 *
 * Streams media to app cache first, then publishes the completed file to
 * MediaStore/Downloads. It never loads an entire media file into RAM.
 */
object MediaDownloadEngine {
    private const val TAG = "LinkShieldDownloader"
    private const val BUFFER_SIZE = 64 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun download(
        context: Context,
        mediaUrl: String,
        filename: String,
        mimeType: String,
        onProgress: (Float) -> Unit = {}
    ): GrabberDownloadResult = withContext(Dispatchers.IO) {
        if (mediaUrl.isBlank()) return@withContext GrabberDownloadResult(false, "Media URL is empty.")
        val safeName = sanitizeFilename(filename, mimeType)
        val temp = File(context.cacheDir, "grab_${System.currentTimeMillis()}_${safeName}")

        try {
            downloadToFile(mediaUrl, temp, onProgress)
            publishToDownloads(context, temp, safeName, mimeType)
            GrabberDownloadResult(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Download failed", t)
            GrabberDownloadResult(false, t.localizedMessage ?: "Download failed.")
        } finally {
            temp.delete()
        }
    }

    suspend fun downloadAndMerge(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        filename: String,
        onProgress: (Float) -> Unit = {}
    ): GrabberDownloadResult = withContext(Dispatchers.IO) {
        val base = "grab_${System.currentTimeMillis()}"
        val video = File(context.cacheDir, "${base}_video.bin")
        val audio = File(context.cacheDir, "${base}_audio.bin")
        val output = File(context.cacheDir, "${base}_merged.mp4")

        try {
            downloadToFile(videoUrl, video) { p -> onProgress(p * 0.45f) }
            downloadToFile(audioUrl, audio) { p -> onProgress(0.45f + p * 0.35f) }

            val command = "-y -i ${q(video.absolutePath)} -i ${q(audio.absolutePath)} " +
                "-map 0:v:0 -map 1:a:0 -c copy -movflags +faststart ${q(output.absolutePath)}"

            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw IllegalStateException("FFmpeg merge failed (code=${session.returnCode?.value ?: -1}).")
            }

            publishToDownloads(context, output, sanitizeFilename(filename, "video/mp4"), "video/mp4")
            onProgress(1f)
            GrabberDownloadResult(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Merge download failed", t)
            GrabberDownloadResult(false, t.localizedMessage ?: "Video/audio merge failed.")
        } finally {
            video.delete(); audio.delete(); output.delete()
        }
    }

    suspend fun downloadAndConvertToMp3(
        context: Context,
        mediaUrl: String,
        filename: String,
        onProgress: (Float) -> Unit = {}
    ): GrabberDownloadResult = withContext(Dispatchers.IO) {
        val base = "grab_${System.currentTimeMillis()}"
        val input = File(context.cacheDir, "${base}_audio_input.bin")
        val output = File(context.cacheDir, "${base}_audio.mp3")

        try {
            downloadToFile(mediaUrl, input) { p -> onProgress(p * 0.75f) }
            val command = "-y -i ${q(input.absolutePath)} -vn -c:a libmp3lame -b:a 192k ${q(output.absolutePath)}"
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw IllegalStateException("FFmpeg MP3 conversion failed (code=${session.returnCode?.value ?: -1}).")
            }
            publishToDownloads(context, output, sanitizeFilename(filename, "audio/mpeg"), "audio/mpeg")
            onProgress(1f)
            GrabberDownloadResult(true)
        } catch (t: Throwable) {
            Log.e(TAG, "MP3 conversion failed", t)
            GrabberDownloadResult(false, t.localizedMessage ?: "MP3 conversion failed.")
        } finally {
            input.delete(); output.delete()
        }
    }

    private fun downloadToFile(url: String, target: File, onProgress: (Float) -> Unit = {}) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LinkShieldSandbox/2.3")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Media server returned HTTP ${response.code}.")
            val body = response.body ?: throw IllegalStateException("Media response was empty.")
            val total = body.contentLength()
            var copied = 0L
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toDouble() / total).toFloat().coerceIn(0f, 1f))
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private fun publishToDownloads(context: Context, source: File, filename: String, mimeType: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LinkShield")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create the Downloads folder.")
            val target = uniqueFile(dir, filename)
            source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) } }
            return
        }

        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LinkShield")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the Downloads file.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
            } ?: throw IllegalStateException("Could not open the Downloads file.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun uniqueFile(dir: File, filename: String): File {
        var candidate = File(dir, filename)
        if (!candidate.exists()) return candidate
        val base = filename.substringBeforeLast('.', filename)
        val ext = filename.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".${it}" }.orEmpty()
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun sanitizeFilename(name: String, mimeType: String): String {
        val raw = name.trim().ifBlank { "LinkShield_Media" }
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim().ifBlank { "LinkShield_Media" }
        val ext = when {
            mimeType.equals("audio/mpeg", true) -> "mp3"
            mimeType.equals("video/mp4", true) -> "mp4"
            else -> cleaned.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) }
        }
        return if (ext.isNullOrBlank()) cleaned else if (cleaned.substringAfterLast('.', "").equals(ext, true)) cleaned else "$cleaned.$ext"
    }

    private fun q(path: String): String = "'" + path.replace("'", "'\\''") + "'"
}
