# UI FREEZE CONTRACT
## LinkShield Sandbox — UI_STABLE_v1.0

**STATUS: ALL SCREENS VISUALLY APPROVED BY PROJECT OWNER**
**DATE LOCKED: Current working build**

---

## LOCKED FILES — TOUCH FORBIDDEN

Yeh files bilkul mat badalna. Sirf project owner ki
explicit permission se modify ho sakti hain.

| File | Kya Control Karta Hai |
|---|---|
| `ui/components/TopHeader.kt` | Main header: logo, shield toggle, DNS dropdown, theme switch, address bar |
| `ui/unblock/UnblockShieldScreen.kt` | 4-tab shell, bottom navigation bar, tab dispatch |
| `ui/screens/OnboardingScreens.kt` | Disclaimer screen + Enable Shield screen |
| `ui/upgrade/UpgradeScreen.kt` | Upgrade tab (free state + pro state) |
| `ui/grabber/LinkShieldGrabberScreen.kt` | Grabber tab layout |
| `ui/theme/Theme.kt` | App theme system |
| `ui/theme/Color.kt` | Brand color palette |
| `res/drawable/ic_app_logo.png` | App logo |
| `res/drawable/ic_easypaisa.png` | EasyPaisa logo |
| `res/drawable/ic_jazzcash.png` | JazzCash logo |
| `res/drawable/ic_usdt.png` | USDT logo |

---

## APPROVED UI STRUCTURE

### Main Screen — 4 Tabs
[ Shield/Browse ] [ Grabber ] [ Check ] [ Upgrade ]

- Default tab on launch: BROWSE
- Tab enum order is FIXED — do not reorder

### TopHeader — 2 Rows
Row 1: [Logo] [Shield Toggle] [DNS Dropdown] [Trial Badge] [Theme Switch]
Row 2: [Back] [Forward] [Reload] [Address Bar] [Go Button]

### Onboarding Flow
App Launch → Disclaimer Screen → Enable Shield Screen → Main App

- Disclaimer: scroll to bottom → Accept & Continue
- Enable Shield: polls every 500ms after settings return

### Upgrade Tab — 2 States
- Free: benefits card + EasyPaisa/JazzCash/USDT payment info + license key input
- Pro: premium member banner + account info

---

## SAFE TO MODIFY — Backend & Logic Only

| Area | File |
|---|---|
| Security check engine | `api/SecurityApiService.kt` |
| DNS-over-HTTPS | `dns/DnsManager.kt` |
| AdBlock engine | `AdBlock/AdBlockEngine.kt` |
| License validation | `license/LicenseManager.kt` |
| WireGuard VPN | `vpn/*.kt` |
| Media extraction | `grabber/MediaExtractorRepository.kt` |
| Download manager | `grabber/GrabberDownloadManger.kt` |
| ViewModel state | `ui/unblock/UnblockShieldViewModel.kt` |
| Browser intercept | `LinkInterceptorActivity.kt` |
| Disclaimer logic | `disclaimer/DisclaimerManager.kt` |

---

## GOLDEN RULES — NEVER BREAK THESE

### Rule 1 — Logo Resource
CORRECT:
painter = painterResource(id = R.drawable.ic_app_logo)

CRASH — adaptive icon XML cannot be decoded by Compose:
painter = painterResource(id = R.mipmap.ic_launcher)

### Rule 2 — No runCatching Around Composables
WRONG — does NOT catch Compose composition errors:
runCatching { UpgradeScreen() }

CORRECT — just call directly:
UpgradeScreen()

### Rule 3 — Tab Enum Order is Fixed
DO NOT reorder or rename:
enum class MainTab { CHECK, BROWSE, GRAB, UPGRADE }

### Rule 4 — UpgradeScreen licenseManager
Always nullable until backend is wired:
UpgradeScreen(licenseManager = null)

### Rule 5 — Theme
- `isDarkTheme` state only lives in `MainActivity`
- Do NOT create a second `ThemeManager` instance anywhere

---

## RECOVERY — Agar UI Toot Jaye

git checkout UI_STABLE_v1.0 -- app/src/main/java/com/linkshield/sandbox/ui/
git checkout UI_STABLE_v1.0 -- app/src/main/res/drawable/
git add -A
git commit -m "Restore: UI recovered from stable tag"
git push origin main

---

## AI ASSISTANT PROMPT TEMPLATE

Jab bhi AI se kaam karwao, pehle yeh paste karo:

SYSTEM CONSTRAINT:
Is project mein UI_FREEZE_CONTRACT.md hai.
Neeche diye locked files ko bilkul mat chhoona:
- ui/components/TopHeader.kt
- ui/unblock/UnblockShieldScreen.kt
- ui/screens/OnboardingScreens.kt
- ui/upgrade/UpgradeScreen.kt
- ui/grabber/LinkShieldGrabberScreen.kt
- ui/theme/Theme.kt aur Color.kt
- res/drawable/ic_app_logo.png

Task sirf yeh hai: [yahan apna kaam likho]
Sirf non-UI files output karo.

---

*Locked at: UI_STABLE_v1.0 — Project owner approved*
