package com.linkshield.sandbox.ui.grabber

/**
 * MediaQualityOption.kt
 *
 * Represents a single downloadable media stream extracted from a page.
 * Used by MediaExtractorRepository to return available streams, and
 * consumed by MediaExtractorViewModel / LinkShieldGrabberScreen for UI.
 *
 * @param url       Direct URL to the media file / stream manifest.
 * @param quality   Quality label extracted from the URL (e.g. "720p"), or "".
 * @param label     Human-readable label shown in the UI dropdown.
 * @param mimeType  MIME type inferred from URL extension (e.g. "video/mp4").
 * @param title     Page title associated with this media item.
 * @param isAudio   True when the stream is audio-only (mimeType starts with "audio/").
 */
data class MediaQualityOption(
    val url: String,
    val quality: String   = "",
    val label: String     = "High Quality",
    val mimeType: String  = "",
    val title: String     = "",
    val isAudio: Boolean  = false
)
