# LinkShield Sandbox — Phase 3 Implementation Notes

## Completed in this phase

1. Added maintained FFmpegKit Android dependency for local media processing.
2. Replaced the Grabber's direct Android DownloadManager path with an app-controlled streaming downloader.
3. Downloads are streamed to a temporary cache file and then published to Android Downloads/MediaStore, avoiding whole-file RAM usage.
4. Added API-29+ MediaStore publishing and a legacy Android Downloads fallback for minSdk 26.
5. Added filename sanitization and duplicate-safe naming.
6. Added local video + audio merge with FFmpeg for yt-dlp separate streams.
7. Added local audio-to-MP3 conversion when the fetched audio is not already MP3.
8. Wired the selected resolution into the Cobalt request.
9. Wired the selected resolution into yt-dlp extraction.
10. Added yt-dlp separate video/audio stream resolution for high-quality YouTube sources.
11. Added a secondary audio URL to the Grabber media result so 1440p/4K can be merged locally when yt-dlp returns separate streams.
12. Moved free-download quota consumption from fetch-success to actual download-success.
13. Kept the existing visual Grabber layout and the previously frozen UI files unchanged visually.

## Important verification limitation

The repository's `gradlew` is not a real Gradle wrapper launcher; it delegates to a system `gradle` executable. The current execution environment does not have system Gradle installed, so a full Android compile could not be completed here.

Before treating this ZIP as release-ready, run:

    ./gradlew :app:assembleDebug --refresh-dependencies

and fix any dependency/API/compiler issue reported by the actual Android build environment.

## FFmpeg dependency

Phase 3 uses:

    implementation 'dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7'

The maintained project documents the same `com.arthenica.ffmpegkit` package/API and publishes the artifact to Maven Central. Verify the project's licensing/redistribution requirements before release.
