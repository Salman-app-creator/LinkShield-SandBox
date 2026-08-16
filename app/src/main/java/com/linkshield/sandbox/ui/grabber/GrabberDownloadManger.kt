package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil

class GrabberDownloadManager(
    private val context: Context
) {

    fun download(
        option: MediaQualityOption
    ): Long? {

        val url = option.url.trim()

        if (url.isBlank()) {
            return null
        }

        return try {
            val fileName =
                URLUtil.guessFileName(
                    url,
                    null,
                    option.mimeType
                        .ifBlank {
                            when {
                                option.isAudioOnly ->
                                    "audio/mpeg"

                                else ->
                                    "video/mp4"
                            }
                        }
                )

            val request =
                DownloadManager.Request(
                    Uri.parse(url)
                ).apply {

                    setTitle(
                        option.displayLabel
                    )

                    setDescription(
                        "Downloading media"
                    )

                    setMimeType(
                        option.mimeType
                            .ifBlank {
                                when {
                                    option.isAudioOnly ->
                                        "audio/mpeg"

                                    else ->
                                        "video/mp4"
                                }
                            }
                    )

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

            val manager =
                context.getSystemService(
                    Context.DOWNLOAD_SERVICE
                ) as? DownloadManager
                    ?: return null

            manager.enqueue(request)
        } catch (_: Exception) {
            null
        }
    }
}
