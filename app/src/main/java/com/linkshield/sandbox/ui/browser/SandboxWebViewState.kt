package com.linkshield.sandbox.ui.browser

import android.webkit.WebBackForwardList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SandboxWebViewState(
    val currentUrl: String = "",
    val title: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

class SandboxWebViewStateViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state =
        MutableStateFlow(
            SandboxWebViewState(
                currentUrl =
                    savedStateHandle[
                        KEY_URL
                    ] ?: "",
                title =
                    savedStateHandle[
                        KEY_TITLE
                    ] ?: ""
            )
        )

    val state:
        StateFlow<SandboxWebViewState> =
        _state.asStateFlow()

    fun update(
        url: String,
        title: String,
        history: WebBackForwardList?
    ) {
        val newState =
            SandboxWebViewState(
                currentUrl = url,
                title = title,
                canGoBack =
                    history?.currentIndex
                        ?.let { it > 0 }
                        ?: false,
                canGoForward =
                    history?.let {
                        it.currentIndex <
                            it.size - 1
                    } ?: false
            )

        _state.value = newState

        savedStateHandle[
            KEY_URL
        ] = url

        savedStateHandle[
            KEY_TITLE
        ] = title
    }

    fun updateUrl(
        url: String
    ) {
        _state.value =
            _state.value.copy(
                currentUrl = url
            )

        savedStateHandle[
            KEY_URL
        ] = url
    }

    fun clear() {
        _state.value =
            SandboxWebViewState()

        savedStateHandle[
            KEY_URL
        ] = ""

        savedStateHandle[
            KEY_TITLE
        ] = ""
    }

    companion object {
        private const val KEY_URL =
            "sandbox_webview_url"

        private const val KEY_TITLE =
            "sandbox_webview_title"
    }
}
