package com.linkshield.sandbox.ui.grabber

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaSnifferState {

    private val _latestMedia = MutableStateFlow<CapturedMediaItem?>(null)
    val latestMedia: StateFlow<CapturedMediaItem?> = _latestMedia.asStateFlow()

    private val _mediaUrls = MutableStateFlow<List<CapturedMediaItem>>(emptyList())
    val mediaUrls: StateFlow<List<CapturedMediaItem>> = _mediaUrls.asStateFlow()

    fun publish(item: CapturedMediaItem) {
        if (item.url.isBlank()) return

        _latestMedia.value = item
        val current = _mediaUrls.value
        if (current.any { it.url == item.url }) return
        _mediaUrls.value = (listOf(item) + current).take(50)
    }

    fun publish(
        url: String,
        title: String = "",
        pageUrl: String = "",
        mimeType: String = "",
        extension: String = ""
    ) {
        if (url.isBlank()) return
        publish(CapturedMediaItem(url, title, pageUrl, mimeType, extension))
    }

    fun clear() {
        _latestMedia.value = null
        _mediaUrls.value = emptyList()
    }
}
