package com.linkshield.sandbox.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SandboxBrowserUiState(
    val url: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

class SandboxBrowserViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            SandboxBrowserUiState()
        )

    val uiState:
        StateFlow<SandboxBrowserUiState> =
        _uiState.asStateFlow()

    fun onPageStarted(
        url: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                url = url,
                isLoading = true
            )
    }

    fun onPageFinished(
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                url = url,
                title = title,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
    }

    fun updateProgress(
        progress: Int
    ) {
        _uiState.value =
            _uiState.value.copy(
                progress =
                    progress.coerceIn(
                        0,
                        100
                    )
            )
    }

    fun updateHistory(
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
    }

    fun updateUrl(
        url: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                url = url
            )
    }

    fun goBack(): Boolean {
        return SandboxWebViewSession
            .goBack()
    }

    fun goForward(): Boolean {
        return SandboxWebViewSession
            .goForward()
    }

    fun reload() {
        SandboxWebViewSession.reload()
    }

    fun currentUrl(): String {
        return SandboxWebViewSession
            .currentUrl()
    }

    fun hasActiveSession(): Boolean {
        return SandboxWebViewSession
            .hasSession()
    }
        fun syncFromWebView() {
        val webView =
            SandboxWebViewSession.get()
                ?: return

        _uiState.value =
            _uiState.value.copy(
                url =
                    webView.url.orEmpty(),
                title =
                    webView.title.orEmpty(),
                canGoBack =
                    webView.canGoBack(),
                canGoForward =
                    webView.canGoForward()
            )
    }

    fun clearLoading() {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false
            )
    }

    override fun onCleared() {
        /*
         * WebView is intentionally NOT destroyed here.
         *
         * The browser session can be reused when
         * returning from Grabber.
         */
        super.onCleared()
    }
}
