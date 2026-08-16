package com.linkshield.sandbox.data

import android.content.Context
import com.linkshield.sandbox.ui.grabber.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MediaExtractionResult(
    val success: Boolean,
    val title: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val options: List<MediaQualityOption> = emptyList(),
    val error: String? = null
)

class MediaExtractorRepository(
    private val context: Context
) {

    suspend fun extract(
        sourceUrl: String
    ): MediaExtractionResult =
        withContext(Dispatchers.IO) {

            if (sourceUrl.isBlank()) {
                return@withContext MediaExtractionResult(
                    success = false,
                    error = "URL is empty"
                )
            }

            return@withContext try {
                extractWithCobalt(
                    sourceUrl.trim()
                )
            } catch (e: Exception) {
                MediaExtractionResult(
                    success = false,
                    error = e.message
                        ?: "Media extraction failed"
                )
            }
        }

    private fun extractWithCobalt(
        sourceUrl: String
    ): MediaExtractionResult {

        val endpoint =
            "https://api.cobalt.tools/api/json"

        val connection =
            (URL(endpoint).openConnection()
                as HttpURLConnection).apply {

                requestMethod = "POST"

                connectTimeout = 15_000
                readTimeout = 30_000

                doInput = true
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                setRequestProperty(
                    "User-Agent",
                    "LinkShield/1.0"
                )
            }

        val body =
            JSONObject().apply {
                put(
                    "url",
                    sourceUrl
                )

                put(
                    "downloadMode",
                    "auto"
                )

                put(
                    "audioFormat",
                    "mp3"
                )

                put(
                    "videoQuality",
                    "1080"
                )

                put(
                    "youtubeVideoCodec",
                    "h264"
                )

                put(
                    "youtubeAudioFormat",
                    "mp3"
                )
            }

        connection.outputStream.use {
            it.write(
                body.toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )
        }

        val status =
            connection.responseCode

        val stream =
            if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val response =
            stream?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

        connection.disconnect()

        if (response.isBlank()) {
            return MediaExtractionResult(
                success = false,
                error = "Empty extractor response"
            )
        }

        return parseCobaltResponse(
            response
        )
    }

    private fun parseCobaltResponse(
        rawResponse: String
    ): MediaExtractionResult {

        val json =
            JSONObject(rawResponse)

        val status =
            json.optString(
                "status"
            )

        if (
            status.equals(
                "error",
                true
            )
        ) {
            return MediaExtractionResult(
                success = false,
                error =
                    json.optJSONObject(
                        "error"
                    )?.optString(
                        "code"
                    )
                        ?: "Extractor returned an error"
            )
        }

        val title =
            json.optString(
                "title"
            )

        val thumbnail =
            json.optString(
                "thumbnail"
            )

        val duration =
            json.optString(
                "duration"
            )

        val options =
            mutableListOf<MediaQualityOption>()

        val directUrl =
            json.optString(
                "url"
            )

        if (directUrl.isNotBlank()) {
            options += MediaQualityOption(
                id = "cobalt_auto",
                label = "Best Quality",
                url = directUrl,
                quality = "Best",
                format =
                    json.optString(
                        "format"
                    ),
                mimeType =
                    json.optString(
                        "mimeType"
                    ),
                extension =
                    json.optString(
                        "extension"
                    ),
                isAudioOnly = false,
                isVideo = true
            )
        }

        val audioUrl =
            json.optString(
                "audio"
            )

        if (audioUrl.isNotBlank()) {
            options += MediaQualityOption(
                id = "cobalt_audio",
                label = "Audio Only",
                url = audioUrl,
                quality = "Audio",
                format = "mp3",
                extension = "mp3",
                isAudioOnly = true,
                isVideo = false
            )
        }

        parseMediaArray(
            json.optJSONArray("formats"),
            options
        )

        parseMediaArray(
            json.optJSONArray("media"),
            options
        )

        val uniqueOptions =
            options
                .filter {
                    it.url.isNotBlank()
                }
                .distinctBy {
                    it.url
                }

        if (uniqueOptions.isEmpty()) {
            return MediaExtractionResult(
                success = false,
                title = title,
                thumbnail = thumbnail,
                duration = duration,
                error = "No downloadable media found"
            )
        }

        return MediaExtractionResult(
            success = true,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            options = uniqueOptions
        )
    }
        private fun parseMediaArray(
        array: JSONArray?,
        output: MutableList<MediaQualityOption>
    ) {
        if (array == null) {
            return
        }

        for (index in 0 until array.length()) {
            val item =
                array.optJSONObject(index)
                    ?: continue

            val url =
                item.optString("url")
                    .ifBlank {
                        item.optString("downloadUrl")
                    }

            if (url.isBlank()) {
                continue
            }

            val height =
                item.optInt(
                    "height",
                    0
                ).takeIf {
                    it > 0
                }

            val width =
                item.optInt(
                    "width",
                    0
                ).takeIf {
                    it > 0
                }

            val format =
                item.optString(
                    "format"
                )

            val extension =
                item.optString(
                    "extension"
                ).ifBlank {
                    format
                }

            val mime =
                item.optString(
                    "mimeType"
                ).ifBlank {
                    item.optString(
                        "mime"
                    )
                }

            val audioOnly =
                item.optBoolean(
                    "audioOnly",
                    false
                ) ||
                    (
                        mime.startsWith(
                            "audio/",
                            true
                        )
                    )

            val label =
                when {
                    audioOnly ->
                        "Audio Only"

                    height != null ->
                        "${height}p"

                    item.optString(
                        "quality"
                    ).isNotBlank() ->
                        item.optString(
                            "quality"
                        )

                    else ->
                        "Media ${index + 1}"
                }

            val quality =
                item.optString(
                    "quality"
                ).ifBlank {
                    label
                }

            val bitrate =
                item.optLong(
                    "bitrate",
                    0L
                ).takeIf {
                    it > 0
                }

            val filesize =
                item.optLong(
                    "filesize",
                    0L
                ).takeIf {
                    it > 0
                }

            val fps =
                item.optDouble(
                    "fps",
                    0.0
                ).takeIf {
                    it > 0
                }

            output += MediaQualityOption(
                id = "format_$index",
                label = label,
                url = url,
                quality = quality,
                format = format,
                mimeType = mime,
                extension = extension,
                bitrate = bitrate,
                width = width,
                height = height,
                fps = fps,
                filesize = filesize,
                isAudioOnly = audioOnly,
                isVideo = !audioOnly
            )
        }
    }

    suspend fun extractBest(
        sourceUrl: String,
        audioOnly: Boolean = false
    ): MediaQualityOption? =
        withContext(Dispatchers.IO) {

            val result =
                extract(sourceUrl)

            if (!result.success) {
                return@withContext null
            }

            val options =
                result.options

            if (audioOnly) {
                return@withContext options
                    .filter {
                        it.isAudioOnly
                    }
                    .maxByOrNull {
                        it.bitrate ?: 0L
                    }
                    ?: options.firstOrNull()
            }

            options
                .filter {
                    it.isVideo
                }
                .maxWithOrNull(
                    compareBy<MediaQualityOption> {
                        it.height ?: 0
                    }.thenBy {
                        it.bitrate ?: 0L
                    }
                )
                ?: options.firstOrNull()
        }
}
