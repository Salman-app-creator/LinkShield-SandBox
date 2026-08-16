package com.linkshield.sandbox.ui.grabber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkshield.sandbox.api.ExpandedUrlResult
import com.linkshield.sandbox.api.NetworkStatus
import com.linkshield.sandbox.api.NetworkStatusRepository
import com.linkshield.sandbox.api.SecurityApiService
import com.linkshield.sandbox.api.ThreatCheckResult
import com.linkshield.sandbox.data.MediaExtractionResult
import com.linkshield.sandbox.data.MediaExtractorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GrabberUiState(
    val address: String = "",
    val title: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val qualities: List<MediaQualityOption> =
        emptyList(),
    val selectedQualityId: String? = null,
    val isExtracting: Boolean = false,
    val isExpanding: Boolean = false,
    val isCheckingThreat: Boolean = false,
    val threat: ThreatCheckResult? = null,
    val expanded: ExpandedUrlResult? = null,
    val network: NetworkStatus = NetworkStatus(),
    val error: String? = null
)

class GrabberViewModel(
    private val extractor:
        MediaExtractorRepository,
    private val security:
        SecurityApiService =
            SecurityApiService(),
    private val network:
        NetworkStatusRepository =
            NetworkStatusRepository()
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            GrabberUiState()
        )

    val uiState:
        StateFlow<GrabberUiState> =
        _uiState.asStateFlow()

    private val _latestMedia =
        MutableStateFlow<
            CapturedMediaItem?
        >(null)

    val latestMedia:
        StateFlow<CapturedMediaItem?> =
        _latestMedia.asStateFlow()

    fun setAddress(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                address = value,
                error = null
            )
    }

    fun setLatestMedia(
        item: CapturedMediaItem?
    ) {
        _latestMedia.value = item

        if (item == null) {
            return
        }

        if (item.url.isBlank()) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                address = item.url,
                title = item.title,
                error = null
            )
    }

    fun selectQuality(
        id: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                selectedQualityId = id
            )
    }

    fun clearThreat() {
        _uiState.value =
            _uiState.value.copy(
                threat = null
            )
    }

    fun clearError() {
        _uiState.value =
            _uiState.value.copy(
                error = null
            )
    }

    fun inspectUrl(
        rawUrl: String
    ) {
        val url =
            rawUrl.trim()

        if (url.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    error =
                        "Enter a URL first"
                )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                address = url,
                isExpanding = true,
                isCheckingThreat = true,
                error = null,
                threat = null
            )

        viewModelScope.launch(
            Dispatchers.IO
        ) {
            try {
                val result =
                    security.checkAndExpand(
                        url
                    )

                val threat =
                    result.first

                val expanded =
                    result.second

                val target =
                    if (
                        expanded.success &&
                        expanded.expandedUrl
                            .isNotBlank()
                    ) {
                        expanded.expandedUrl
                    } else {
                        url
                    }

                _uiState.value =
                    _uiState.value.copy(
                        address = target,
                        expanded = expanded,
                        threat = threat,
                        isExpanding = false,
                        isCheckingThreat = false
                    )
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isExpanding = false,
                        isCheckingThreat = false,
                        error =
                            e.message
                                ?: "URL check failed"
                    )
            }
        }
    }

    fun extractMedia(
        rawUrl: String =
            _uiState.value.address
    ) {
        val url =
            rawUrl.trim()

        if (url.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    error =
                        "Enter a URL first"
                )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                address = url,
                isExtracting = true,
                error = null,
                qualities = emptyList(),
                selectedQualityId = null
            )

        viewModelScope.launch(
            Dispatchers.IO
        ) {
            try {
                val result =
                    extractor.extract(
                        url
                    )

                applyExtraction(
                    result
                )
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isExtracting = false,
                        error =
                            e.message
                                ?: "Extraction failed"
                    )
            }
        }
    }

    private fun applyExtraction(
        result:
            MediaExtractionResult
    ) {
        if (!result.success) {
            _uiState.value =
                _uiState.value.copy(
                    isExtracting = false,
                    error =
                        result.error
                            ?: "No media found"
                )

            return
        }

        val first =
            result.options.firstOrNull()

        _uiState.value =
            _uiState.value.copy(
                title =
                    result.title.ifBlank {
                        _uiState.value.title
                    },
                thumbnail =
                    result.thumbnail,
                duration =
                    result.duration,
                qualities =
                    result.options,
                selectedQualityId =
                    first?.id,
                isExtracting = false,
                error = null
            )
    }
        fun refreshNetworkStatus() {
        _uiState.value =
            _uiState.value.copy(
                network =
                    _uiState.value.network.copy(
                        isLoading = true,
                        error = null
                    )
            )

        viewModelScope.launch(
            Dispatchers.IO
        ) {
            try {
                val status =
                    network.fetchStatus()

                _uiState.value =
                    _uiState.value.copy(
                        network = status
                    )
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        network =
                            _uiState.value.network.copy(
                                isLoading = false,
                                error =
                                    e.message
                                        ?: "Network status failed"
                            )
                    )
            }
        }
    }

    fun latestOption():
        MediaQualityOption? {
        val state =
            _uiState.value

        return state.qualities
            .firstOrNull {
                it.id ==
                    state.selectedQualityId
            }
            ?: state.qualities
                .firstOrNull()
    }

    fun downloadUrl():
        String? {
        return latestOption()?.url
    }

    fun isThreatBlocked():
        Boolean {
        return _uiState.value
            .threat
            ?.isMalicious == true
    }

    fun resetExtraction() {
        _uiState.value =
            _uiState.value.copy(
                title = "",
                thumbnail = "",
                duration = "",
                qualities = emptyList(),
                selectedQualityId = null,
                isExtracting = false,
                error = null
            )
    }

    fun syncLatestCapturedMedia() {
        val item =
            MediaSnifferState
                .latestMedia
                .value

        if (item != null) {
            setLatestMedia(item)
        }
    }

    override fun onCleared() {
        _latestMedia.value = null
        super.onCleared()
    }
}
