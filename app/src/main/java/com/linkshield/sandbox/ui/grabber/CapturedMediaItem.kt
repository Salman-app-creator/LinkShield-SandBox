package com.linkshield.sandbox.ui.grabber

/**
 * Lightweight snapshot of a media item captured by the Browser WebView JS bridge.
 */
data class CapturedMediaItem(
    val url:       String,
    val title:     String,
    val pageUrl:   String,
    val timestamp: Long = System.currentTimeMillis()
)
