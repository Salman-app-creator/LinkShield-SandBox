package com.linkshield.sandbox.ui.grabber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaExtractorState(
    val isLoading: Boolean = false,
    val options: List<MediaQualityOption> =
        emptyList(),
    val selected: MediaQualityOption? = null,
    val error: String? = null
)

class MediaExtractorViewModel(
    private val repository:
        MediaExtractorRepository =
            MediaExtractorRepository()
) : ViewModel() {

    private val _state =
        MutableStateFlow(
            MediaExtractorState()
        )

    val state:
        StateFlow<MediaExtractorState> =
        _state.asStateFlow()

    fun extract(
        url: String,
        title: String = ""
    ) {
        if (url.isBlank()) {
            _state.value =
                _state.value.copy(
                    error = "Media URL is empty"
                )
            return
        }

        viewModelScope.launch(
            Dispatchers.IO
        ) {
            _state.value =
                _state.value.copy(
                    isLoading = true,
                    error = null
                )

            try {
                val options =
                    repository.extract(
                        mediaUrl = url,
                        title = title
                    )

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        options = options,
                        selected =
                            options.firstOrNull()
                    )
                                } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error =
                            e.message
                                ?: "Extraction failed"
                    )
            }
        }
    }

    fun select(
        option: MediaQualityOption
    ) {
        _state.value =
            _state.value.copy(
                selected = option
            )
    }

    fun clear() {
        _state.value =
            MediaExtractorState()
    }
}

class MediaExtractorViewModelFactory(
    private val repository:
        MediaExtractorRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                MediaExtractorViewModel::class.java
            )
        ) {
            return MediaExtractorViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel: " +
                modelClass.name
        )
    }
}
