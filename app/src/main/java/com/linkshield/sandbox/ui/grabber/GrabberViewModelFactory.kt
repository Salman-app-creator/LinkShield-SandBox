package com.linkshield.sandbox.ui.grabber

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class GrabberViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                GrabberViewModel::class.java
            )
        ) {
            return GrabberViewModel(
                application
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel: ${modelClass.name}"
        )
    }
}
