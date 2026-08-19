# LinkShield UI Refactor — Top Header Scope Fix

## Changed
- `ui/components/TopHeader.kt`
  - Browser-only header design.
  - Shield ON/OFF is now a functional dropdown.
  - Removed Cloudflare/Google/Quad9/AdGuard server selector from Row 1.
  - Logo is the only element spanning the two header rows.
  - Kept other controls compact and standard-sized.
- `ui/browser/SandboxBrowserScreen.kt`
  - Owns the only `TopHeader` instance.
  - Browser WebView remains below the header.
- `ui/unblock/UnblockShieldScreen.kt`
  - Removed global `Scaffold(topBar = TopHeader(...))`.
  - Browse tab hosts `SandboxBrowserScreen`.
  - Check, Grabber, and Upgrade tabs no longer receive the browser header.
  - Upgrade tab remains present in bottom navigation.
- `ui/grabber/LinkShieldGrabberScreen.kt`
  - Removed layout dependency on a top header.
  - Added vertical scrolling and bottom-safe spacing so the action remains reachable.
- `ui/upgrade/UpgradeScreen.kt`
  - Removed redundant logo block.
  - Added premium gradient Pro Benefits card with distinct check icons.
  - Added EasyPaisa, JazzCash, and USDT brand-style payment icons.

## Backend protection
No files under AdBlock, VPN, DNS, sniffer, or API engine implementation were intentionally modified by this UI refactor.

## Build note
The supplied project does not contain a real Gradle wrapper; `gradlew` delegates to a system `gradle` executable. The current execution environment has no `gradle` binary, so a full Android compilation could not be run here.
