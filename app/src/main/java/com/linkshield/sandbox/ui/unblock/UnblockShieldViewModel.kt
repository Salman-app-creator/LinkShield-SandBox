package com.linkshield.sandbox.ui.unblock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class UnblockShieldViewModel : ViewModel() {

    data class MediaItem(
        val url: String,
        val type: String,
        val title: String? = null
    )

    var currentUrl by mutableStateOf(
        "https://google.com"
    )
        private set

    var isFullScreen by mutableStateOf(
        false
    )
        private set

    var customViewCallback:
        WebChromeClient.CustomViewCallback? =
        null
        private set

    val capturedMediaList =
        mutableStateListOf<MediaItem>()

    fun updateUrl(url: String) {
        currentUrl = url
    }

    fun addCapturedMedia(
        url: String,
        type: String,
        title: String? = null
    ) {
        if (
            capturedMediaList.none {
                it.url == url
            }
        ) {
            capturedMediaList.add(
                MediaItem(
                    url = url,
                    type = type,
                    title = title
                )
            )
        }
    }

    fun clearCapturedMedia() {
        capturedMediaList.clear()
    }

    fun showCustomView(
        callback:
            WebChromeClient.CustomViewCallback?
    ) {
        customViewCallback = callback
        isFullScreen = true
    }
        fun hideCustomView() {
        val callback =
            customViewCallback

        customViewCallback = null
        isFullScreen = false

        callback?.onCustomViewHidden()
    }

    fun openExternalBrowser(
        context: Context,
        url: String
    ) {
        try {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )

            context.startActivity(intent)
        } catch (
            e: Exception
        ) {
            e.printStackTrace()
        }
    }
}
