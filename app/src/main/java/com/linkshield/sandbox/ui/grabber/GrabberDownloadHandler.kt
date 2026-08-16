package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast

class GrabberDownloadHandler(
    context: Context
) {

    private val manager =
        GrabberDownloadManager(
            context.applicationContext
        )

    fun download(
        option: MediaQualityOption
    ) {
        val id =
            manager.download(option)

        if (id == null) {
            Toast.makeText(
                context,
                "Unable to start download",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Toast.makeText(
            context,
            "Download started",
            Toast.LENGTH_SHORT
        ).show()
    }
}
