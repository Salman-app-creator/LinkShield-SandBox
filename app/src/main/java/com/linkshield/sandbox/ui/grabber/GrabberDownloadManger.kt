package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil

class GrabberDownloadManager(
    private val context: Context
) {

    private val downloadManager =
        context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    fun download(
        option: MediaQualityOption
    ): Long? {
        val url = option.url.trim()

        if (url.isBlank()) {
            return null
        }

        if (
            !url.startsWith("http://") &&
            !url.startsWith("https://")
        ) {
            return null
        }

        return try {
            val fileName =
                createFileName(
                    option
                )

            val request =
                DownloadManager.Request(
                    Uri.parse(url)
                ).apply {
                    setTitle(
                        option.title.ifBlank {
                            fileName
                        }
                    )

                    setDescription(
                        "LinkShield Sandbox"
                    )

                    if (option.mimeType.isNotBlank()) {
                        setMimeType(option.mimeType)
                    }

                    setNotificationVisibility(
                        DownloadManager
                            .Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )

                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)

                    setDestinationInExternalPublicDir(
                        Environment
                            .DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }

            downloadManager.enqueue(
                request
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun createFileName(
        option: MediaQualityOption
    ): String {
        val title =
            option.title
                .ifBlank {
                    URLUtil.guessFileName(
                        option.url,
                        null,
                        option.mimeType
                    )
                }
                .replace(
                    Regex("[\\\\/:*?\"<>|]"),
                    "_"
                )
                .trim()

        val extension =
            extensionFor(
                option
            )

        return if (
            title.endsWith(
                extension,
                ignoreCase = true
            )
        ) {
            title
        } else {
            "$title$extension"
        }
    }
        private fun extensionFor(
        option: MediaQualityOption
    ): String {
        val mime =
            option.mimeType
                .lowercase()

        return when {
            mime.contains("mpegurl") ->
                ".m3u8"

            mime.contains("mp4") ->
                ".mp4"

            mime.contains("webm") ->
                ".webm"

            mime.contains("mpeg") ||
                mime.contains("mp3") ->
                ".mp3"

            mime.contains("m4a") ||
                mime.contains("mp4a") ->
                ".m4a"

            else -> {
                val url =
                    option.url
                        .substringBefore("?")
                        .substringBefore("#")

                val ext =
                    url.substringAfterLast(
                        '.',
                        ""
                    )

                if (
                    ext.isNotBlank() &&
                    ext.length <= 5
                ) {
                    ".$ext"
                } else {
                    ".mp4"
                }
            }
        }
    }
}
