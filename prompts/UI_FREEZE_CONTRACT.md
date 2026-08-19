# LinkShield UI Freeze Contract

This document supplements `LINKSHIELD_MASTER_SPEC.md` and is part of the UI-frozen project baseline.

## Scope

This baseline freezes only Jetpack Compose presentation, navigation, theme, onboarding, browser presentation, Grabber presentation, and Upgrade presentation.

Backend engines are intentionally not executed by the frozen UI:
- AdBlock
- WireGuard VPN
- Web Sniffer
- DNS / DoH
- Security / URL analysis
- Cobalt/media extraction
- Download engine
- License validation

Where a screen previously depended on an engine or ViewModel, the frozen UI uses local `rememberSaveable` state and deterministic demo/static data instead.

## Main navigation

Bottom navigation contains exactly four primary destinations:
1. Check
2. Browse
3. Grabber
4. Upgrade

Secure Network is intentionally accessed from the top-header security/DNS dropdown and is not a fifth bottom tab.

## Top header

- App logo is the LinkShield Sandbox logo and is displayed as a true circle.
- Header is compact and consumes no unnecessary top blank area.
- Shield state, DNS/security menu, trial badge, and theme switch live in the first header row.
- Browser back/forward/reload controls and URL field live in the second row.
- DNS/security menu remains the presentation entry point for Secure Network.

## Browser

- Browser is the Browse destination.
- Back/forward/reload state is presentation state.
- Renderer failure is handled without allowing a renderer crash to terminate the Activity.
- No ad-block, sniffer, DNS, security, or download engine is invoked from the frozen UI.

## Grabber

The layout is frozen as:
- Top info banner: `20 Free Downloads Remaining` + `Upgrade to Pro for Unlimited`
- Input address bar: `Paste or Fetch Link...`
- Media Preview Area
- Options: Audio Only / High Qual
- Primary Download button
- Active back button returns to the current browser destination, not the home screen.

Grabber interactions are currently UI-only and use deterministic demo state until the Cobalt/download backend is integrated.

## Upgrade

Upgrade is a dedicated bottom destination. Payment information and license-key entry remain presentation-only in this phase. License validation is not executed by the frozen UI.

## State persistence

User-visible navigation, input, toggles, and onboarding-stage state use `rememberSaveable` where appropriate so configuration changes do not unexpectedly reset the presentation state.

## Assets

`ic_app_logo.png` is stored with transparent pixels outside the circular logo so it does not render as a square on UI surfaces or launcher foreground usage.
