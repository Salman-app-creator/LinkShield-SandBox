package com.linkshield.sandbox.ui.grabber

data class MediaQualityOption(
    val url: String,
    val quality: String = "",
    val label: String = "",
    val mimeType: String = "",
    val title: String = "",
    val isAudio: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null
) {
    val displayLabel: String
        get() {
            if (label.isNotBlank()) {
                return label
            }

            if (isAudio) {
                return "Audio Only"
            }

            if (quality.isNotBlank()) {
                return quality
            }

            return when {
                height != null ->
                    "${height}p"

                width != null ->
                    "${width}p"

                else ->
                    "High Quality"
            }
        }
}
