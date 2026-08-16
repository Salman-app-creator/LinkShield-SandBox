package com.linkshield.sandbox.ui.grabber

data class CapturedMediaItem(
    val url: String,
    val title: String = "",
    val pageUrl: String = "",
    val mimeType: String = "",
    val extension: String = ""
)
