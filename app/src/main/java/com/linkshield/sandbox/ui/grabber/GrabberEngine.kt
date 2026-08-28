package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/GrabberEngine.kt
//
// NOTE: YoutubeDL-android library hata di gayi hai.
// Ab Cobalt API (Oracle port 9001) use ho raha hai — CobaltApiService.kt mein.
// Yeh file sirf backward-compat ke liye rahi hai taake LinkShieldApp.kt crash na kare.

import android.content.Context
import android.util.Log

object GrabberEngine {
    private const val TAG = "GrabberEngine"

    fun init(context: Context) {
        Log.d(TAG, "GrabberEngine: using Cobalt API, no local engine needed")
    }

    fun updateExtractor(context: Context) {
        Log.d(TAG, "GrabberEngine: no update needed, Cobalt API is server-side")
    }

    fun isReady(): Boolean = true
}
