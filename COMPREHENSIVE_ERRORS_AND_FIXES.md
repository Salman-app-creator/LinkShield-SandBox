# LinkShield Sandbox — Comprehensive Errors & Fixes

## UI protection rule

The approved UI is frozen. The following files were NOT modified in Phase 2:

```text
ui/TopHeader.kt
ui/unblock/UnblockShieldScreen.kt
ui/screens/OnboardingScreens.kt
ui/upgrade/UpgradeScreen.kt
ui/grabber/LinkShieldGrabberScreen.kt
ui/theme/Theme.kt
ui/theme/Color.kt
```

The locked Grabber screen was byte-for-byte unchanged against the original project.

---

# PHASE 1 + PHASE 2 FIXES

## 1. Cobalt was using obsolete endpoints

### Old problem

```text
http://141.148.223.177:9001/api/json
http://141.148.223.177:9002/api/json
```

The app selected different old endpoints for YouTube and other sites.

### Fix

Use the current self-hosted Cobalt base URL and send the request to `POST /`:

```kotlin
private fun baseUrl(): String {
    return BuildConfig.COBALT_BASE_URL.trim().trimEnd('/') + "/"
}
```

```kotlin
Request.Builder()
    .url(baseUrl())
    .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
    .header("Accept", "application/json")
    .header("Content-Type", "application/json")
```

Current Cobalt API documentation defines `POST /` as the main processing endpoint. citeturn11search0

---

## 2. Cobalt base URL was not centralized

### Fix

```groovy
buildConfigField "String", "COBALT_BASE_URL", "\"http://141.148.223.177:9000/\""
buildConfigField "String", "COBALT_API_KEY", "\"\""
```

No API secret is hard-coded.

> Production recommendation: use HTTPS and keep any API key out of source control.

---

## 3. Cobalt response statuses were incomplete

### Problem

The backend only handled a subset of possible Cobalt responses.

### Fix

The client now handles:

```text
status = tunnel
status = redirect
status = picker
status = local-processing
status = error
```

Cobalt documents these response states. citeturn11search0

---

## 4. Cobalt error context was parsed incorrectly

### Old pattern

```kotlin
val context = errorObject?.optString("context").orEmpty()
```

`context` is an object, not a normal string.

### Fix

```kotlin
val contextObject = errorObject?.optJSONObject("context")
val service = contextObject?.optString("service").orEmpty()
val limit = contextObject?.optInt("limit", -1)?.takeIf { it >= 0 }
```

---

## 5. YouTube had no local yt-dlp engine

### Problem

The original project had no real yt-dlp integration.

### Fix

Added:

```text
app/src/main/java/com/linkshield/sandbox/ui/grabber/YtDlpEngine.kt
```

and dependency:

```groovy
implementation 'dev.ffmpegkit-maintained:yt-dlp-android:2.0.2'
```

The maintained free library provides a Java API and bundles yt-dlp + Python inside the AAR. citeturn10search0

---

## 6. yt-dlp initialization could block app startup

### Problem

Initializing the embedded Python/yt-dlp runtime directly inside `Application.onCreate()` could contribute to the white-screen startup delay that had already been observed.

### Bad

```kotlin
YtDlpEngine.initialize(this)
```

on the main application thread.

### Fix

```kotlin
appScope.launch(Dispatchers.IO) {
    runCatching {
        YtDlpEngine.initialize(this@LinkShieldApp)
    }.onFailure {
        Log.w("LinkShieldApp", "yt-dlp initialization failed: ${it.message}")
    }
}
```

The UI remains independent of yt-dlp initialization.

---

## 7. yt-dlp could be initialized multiple times

### Fix

`YtDlpEngine` now uses an atomic initialization guard:

```kotlin
private val initialized = AtomicBoolean(false)
```

and synchronized initialization:

```kotlin
if (initialized.get()) return
synchronized(initialized) {
    if (initialized.get()) return
    YtDlp.init(context.applicationContext)
    initialized.set(true)
}
```

---

## 8. The previous Grabber download method was fake

### Old problem

The old engine returned success without actually downloading anything:

```kotlin
GrabberDownloadResult(success = true)
```

### Fix

The unconditional success stub was removed.

The backend now requires successful media extraction before reporting success.

---

## 9. Grabber was using Cobalt directly instead of a proper backend layer

### Problem

The UI calls the Cobalt service directly. The repository/engine abstraction existed but was not the real execution path.

### Current safe approach

Because the UI is frozen, Phase 2 keeps the UI call unchanged and upgrades the backend behind the same API:

```kotlin
val result = cobaltService.fetchMediaUrl(
    rawUrl = clean,
    audioOnly = audioOnly
)
```

The Cobalt service now internally routes YouTube video requests through yt-dlp first and falls back to Cobalt.

---

## 10. YouTube URL normalization

### Problem

Different YouTube URL forms could reach the extractor:

```text
https://youtu.be/VIDEO_ID
https://www.youtube.com/watch?v=VIDEO_ID
https://youtube.com/shorts/VIDEO_ID
```

### Fix

Canonicalize normal YouTube watch URLs:

```kotlin
val videoId = uri.getQueryParameter("v")
if (!videoId.isNullOrBlank()) {
    "https://www.youtube.com/watch?v=$videoId"
}
```

and short links:

```kotlin
val videoId = uri.lastPathSegment
if (!videoId.isNullOrBlank()) {
    "https://www.youtube.com/watch?v=$videoId"
}
```

---

## 11. Non-YouTube query parameters were being stripped

### Problem

The old cleaner rebuilt Instagram/TikTok/Facebook URLs from only the path. That can remove query parameters required by some share URLs.

### Fix

For non-YouTube URLs the original URL is now preserved:

```kotlin
host == "instagram.com" || host.endsWith(".instagram.com") -> trimmed
host == "tiktok.com" || host.endsWith(".tiktok.com") -> trimmed
host == "facebook.com" || host.endsWith(".facebook.com") ||
host == "fb.com" || host.endsWith(".fb.com") || host == "fb.watch" -> trimmed
```

---

## 12. Cobalt audio request format

### Fix

Current API request uses:

```json
{
  "downloadMode": "audio",
  "audioFormat": "mp3",
  "audioBitrate": "128"
}
```

Cobalt documents `audioFormat` and `audioBitrate` as request parameters. citeturn11search0

---

## 13. Cobalt local-processing response

### Current status

The app deliberately requests:

```json
"localProcessing": "disabled"
```

This keeps the existing DownloadManager path simple.

If Cobalt still returns:

```text
status = local-processing
```

the app reports a clear error instead of pretending the media is ready.

### Future fix

Phase 3 can add local FFmpeg processing for Cobalt `local-processing` responses without changing the UI.

---

## 14. Picker response

### Problem

Some platforms can return multiple media items.

### Current backend behaviour

The locked UI expects one downloadable item, so the backend selects a video item when one exists:

```kotlin
var chosen = picker.optJSONObject(0)
for (i in 0 until picker.length()) {
    val item = picker.optJSONObject(i)
    if (item?.optString("type") == "video") {
        chosen = item
        break
    }
}
```

### Future improvement

A multi-item picker UI would require changing the locked Grabber screen, so that is intentionally deferred.

---

## 15. Resolution selector is not fully connected to backend

### Problem

The frozen UI has:

```text
360p / 480p / 720p / 1080p / 4K
```

but its current call does not pass `selectedResolution` into `CobaltApiService`.

### Why not change it now?

`LinkShieldGrabberScreen.kt` is explicitly UI-frozen.

### Current limitation

The backend therefore uses:

```text
1080p
```

for the current direct call.

### Required future fix

A tiny non-visual wiring change is needed in the locked file:

```kotlin
cobaltService.fetchMediaUrl(
    rawUrl = clean,
    audioOnly = audioOnly,
    resolution = selectedResolution
)
```

This should only be done after explicit approval to modify the frozen Grabber logic.

---

## 16. YouTube 4K / 1440p limitation with the current frozen UI

### Problem

yt-dlp can expose separate video and audio streams for high resolutions. The current UI's DownloadManager path expects one direct URL.

### Current safe Phase-2 choice

Prefer a single progressive MP4 format:

```kotlin
.addOption(
    "-f",
    "best[height<=$quality][ext=mp4]/best[height<=$quality]/best"
)
.addOption("-g")
```

This avoids a local merge requirement.

### Consequence

True 1440p/4K YouTube output may require Phase 3 local FFmpeg merging.

---

## 17. DownloadManager direct URL limitation

### Problem

The frozen UI downloads the extracted URL with Android `DownloadManager`.

yt-dlp's direct URLs can sometimes require request headers/cookies. DownloadManager does not automatically inherit yt-dlp's internal request headers.

### Current approach

Use a direct HTTP(S) media URL where possible.

### Future robust approach

For difficult sites/high-quality DASH streams:

```text
yt-dlp -> local download -> FFmpeg merge/transcode -> MediaStore
```

That requires a backend-only download worker and can be implemented without changing the visual UI, but the locked Download button's callback would need to delegate to that worker.

---

## 18. Hard-coded Google Safe Browsing API key

### Problem

The original Gradle file contained a real-looking API key directly in source control.

### Fix

The key is now read from a Gradle property:

```groovy
buildConfigField "String", "SAFE_BROWSING_API_KEY", "\"${project.findProperty('SAFE_BROWSING_API_KEY') ?: ''}\""
```

Local `gradle.properties` example:

```properties
SAFE_BROWSING_API_KEY=YOUR_KEY_HERE
```

If the exposed key was real, it should be rotated/restricted in Google Cloud.

---

# HISTORICAL ERRORS ALREADY IDENTIFIED

## 19. Grabber Kotlin syntax error

Previously reported:

```text
Expecting an expression
is-condition or in-condition
Unresolved reference: fetched
```

Location:

```text
LinkShieldGrabberScreen.kt
```

The current project contains the corrected fetch state flow and `fetched` state declaration.

---

## 20. Fetch loading state could remain stuck

### Fix already present

```kotlin
try {
    // fetch
} catch (e: Exception) {
    // error
} finally {
    isLoading = false
}
```

This prevents the Fetch button from remaining permanently in the loading state after an exception.

---

# VPN ERRORS — NOT YET TOUCHED IN PHASE 2

## 21. Missing tun2socks native binary

The current VPN service looks for:

```text
libtun2socks.so
```

but the project does not contain the required native binary.

Result: Tor can start, but Android's TUN interface has no actual traffic bridge.

### Correct future architecture

```text
Android VpnService
        |
        v
      TUN
        |
        v
HevSocks5Tunnel / tun2socks
        |
        v
Tor SOCKS 127.0.0.1:9050
        |
        v
    Internet
```

This is Phase 4 work and is intentionally isolated from the Grabber/UI repair.

---

## 22. VPN false-positive connected state

### Problem

The service can fail to find `libtun2socks.so`, return, and the caller can still mark the VPN as connected.

### Correct rule

```kotlin
if (!tun2socksStarted) {
    TorVpnManager.setConnected(false)
    return
}

TorVpnManager.setConnected(true)
```

Connection state must represent the real TUN/tun2socks process, not merely Tor SOCKS availability.

---

## 23. Tor SOCKS port is not proof that VPN traffic is routed

Port:

```text
127.0.0.1:9050
```

being open only proves the Tor SOCKS service is listening.

It does NOT prove:

```text
Android traffic -> TUN -> tun2socks -> Tor
```

is working.

---

## 24. VPN work must stay separate from Grabber work

Do not modify VPN code in the same patch as the UI/Grabber stabilization.

Recommended order:

```text
Phase 1  -> Cobalt API repair
Phase 2  -> yt-dlp integration
Phase 3  -> FFmpeg + robust local download/merge
Phase 4  -> HevSocks5Tunnel + Tor VPN
Phase 5  -> end-to-end testing
```

---

# CURRENT ARCHITECTURE

```text
LinkShield
 |
 +-- Browser/UI -------------------- LOCKED
 |
 +-- Grabber UI -------------------- LOCKED
 |       |
 |       +-- CobaltApiService
 |       |       |
 |       |       +-- YouTube -> yt-dlp first
 |       |       |
 |       |       +-- Other sites -> self-hosted Cobalt
 |       |
 |       +-- Android DownloadManager
 |
 +-- VPN --------------------------- Phase 4
 |
 +-- Security / DNS / AdBlock ------ existing backend
```

---

# PHASE 2 FILES CHANGED

```text
app/build.gradle
app/src/main/java/com/linkshield/sandbox/LinkShieldApp.kt
app/src/main/java/com/linkshield/sandbox/api/CobaltApiService.kt
app/src/main/java/com/linkshield/sandbox/ui/grabber/YtDlpEngine.kt
```

# UI FILES CHANGED

```text
NONE
```

# BUILD VERIFICATION NOTE

The supplied project currently contains a `gradlew` script that delegates to a system `gradle` command rather than a normal Gradle Wrapper launcher. The execution environment does not have a system Gradle installation, so a full Android compile could not be executed here.

The project therefore still needs a real Gradle-wrapper/build-machine verification before APK release.
