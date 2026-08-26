package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse

/**
 * GrabberEngine.kt
 *
 * Singleton wrapper around the YoutubeDL-android library.
 * Handles initialization and extractor updates.
 *
 * Called from LinkShieldApp.onCreate() on an IO coroutine — never on main thread.
 *
 * Usage:
 *   GrabberEngine.init(context)            ← must be called once before any fetch
 *   GrabberEngine.updateExtractor(context) ← optional; downloads latest yt-dlp binary
 */
object GrabberEngine {

    private const val TAG = "GrabberEngine"

    @Volatile
    private var initialized = false

    /**
     * Initialize the YoutubeDL engine.
     * Safe to call multiple times — subsequent calls are no-ops.
     *
     * @throws Exception if initialization fails (caller should catch via runCatching)
     */
    fun init(context: Context) {
        if (initialized) return

        YoutubeDL.getInstance().init(context.applicationContext)
        initialized = true
        Log.d(TAG, "GrabberEngine initialized")
    }

    /**
     * Attempt to download the latest yt-dlp binary in the background.
     * Requires an active internet connection.
     * Failures are logged but not rethrown — the engine keeps working
     * with whatever version was previously downloaded.
     *
     * @throws Exception on network failure (caller should catch via runCatching)
     */
    fun updateExtractor(context: Context) {
        val result = YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext)

        Log.d(TAG, "Extractor update result: $result")
    }

    /**
     * Returns true if the engine has been successfully initialized.
     */
    fun isReady(): Boolean = initialized
}
