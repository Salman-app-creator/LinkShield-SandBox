package com.linkshield.sandbox.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SandboxBrowserViewModelFactory :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                SandboxBrowserViewModel::class.java
            )
        ) {
            return SandboxBrowserViewModel()
                as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: " +
                modelClass.name
        )
    }
    }
