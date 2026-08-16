package com.linkshield.sandbox.ui.grabber

data class MediaQualityOption(
    val id: String,
    val label: String,
    val url: String,
    val quality: String = "",
    val format: String = "",
    val mimeType: String = "",
    val extension: String = "",
    val bitrate: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val filesize: Long? = null,
    val isAudioOnly: Boolean = false,
    val isVideo: Boolean = true
) {
    val displayLabel: String
        get() {
            if (label.isNotBlank()) {
                return label
            }

            if (
                height != null &&
                height > 0
            ) {
                return "${height}p"
            }

            if (quality.isNotBlank()) {
                return quality
            }

            if (isAudioOnly) {
                return "Audio Only"
            }

            return "Media"
        }

    val fileExtension: String
        get() {
            if (extension.isNotBlank()) {
                return extension
                    .removePrefix(".")
            }

            if (format.isNotBlank()) {
                return format
                    .removePrefix(".")
            }

            return if (isAudioOnly) {
                "mp3"
            } else {
                "mp4"
            }
        }
}
