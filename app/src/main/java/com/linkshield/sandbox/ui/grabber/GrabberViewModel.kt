package com.linkshield.sandbox.ui.grabber

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GrabberUiState(
    val url: String = "",
    val title: String = "",
    val thumbnail: String? = null,
    val duration: String = "",
    val qualities: List<MediaQualityOption> = emptyList(),
    val selectedQuality: MediaQualityOption? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class GrabberViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState =
        MutableStateFlow(
            GrabberUiState()
        )

    val uiState:
        StateFlow<GrabberUiState> =
        _uiState.asStateFlow()

    init {
        observeCapturedMedia()
    }

    private fun observeCapturedMedia() {
        viewModelScope.launch {
            MediaSnifferState.latestMedia.collect {
                media ->

                if (
                    media != null &&
                    media.url.isNotBlank()
                ) {
                    _uiState.value =
                        _uiState.value.copy(
                            url = media.url
                        )
                }
            }
        }
    }

    fun setUrl(
        url: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                url = url,
                error = null
            )
    }

    fun selectQuality(
        option: MediaQualityOption
    ) {
        _uiState.value =
            _uiState.value.copy(
                selectedQuality = option
            )
    }

    fun clearError() {
        _uiState.value =
            _uiState.value.copy(
                error = null
            )
    }

    fun setPreview(
        title: String,
        thumbnail: String?,
        duration: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                title = title,
                thumbnail = thumbnail,
                duration = duration
            )
    }
    fun setQualities(
        options: List<MediaQualityOption>
    ) {
        _uiState.value =
            _uiState.value.copy(
                qualities = options,
                selectedQuality =
                    options.firstOrNull()
            )
    }

    fun setLoading(
        loading: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = loading
            )
    }

    fun setError(
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                error = message
            )
    }

    fun currentUrl(): String {
        return _uiState.value.url
    }

    fun selectedOption():
        MediaQualityOption? {
        return _uiState.value.selectedQuality
    }
}    
