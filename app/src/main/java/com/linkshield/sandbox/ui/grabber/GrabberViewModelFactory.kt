package com.linkshield.sandbox.ui.grabber

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linkshield.sandbox.data.MediaExtractorRepository

class GrabberViewModelFactory(
    private val context: Context
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
                extractor =
                    MediaExtractorRepository(
                        context.applicationContext
                    )
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: " +
                modelClass.name
        )
    }
}
