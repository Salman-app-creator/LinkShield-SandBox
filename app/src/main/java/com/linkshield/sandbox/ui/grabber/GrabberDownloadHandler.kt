package com.linkshield.sandbox.ui.grabber

import android.content.Context

class GrabberDownloadHandler(
    context: Context
) {

    private val manager =
        GrabberDownloadManager(
            context.applicationContext
        )

    fun download(
        option: MediaQualityOption
    ): Long? {
        return manager.download(option)
    }
}
