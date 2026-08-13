package com.linkshield.sandbox.ui

/**
 * Lightweight snapshot of a media item captured by the Browser WebView JS bridge.
 * Lives in package com.linkshield.sandbox.ui so both UnblockShieldViewModel
 * and MediaGrabberScreen can reference it without cross-package imports.
 */
data class CapturedMediaItem(
    val url:       String,
    val title:     String,
    val pageUrl:   String,
    val timestamp: Long = System.currentTimeMillis()
)
