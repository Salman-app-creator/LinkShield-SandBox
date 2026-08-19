================================================================================
MASTER SYSTEM INSTRUCTION & UNIFIED BLUEPRINT: LINKSHIELD SANDBOX
================================================================================

Role: You are a Lead Mobile Systems Architect & Senior Android Engineer specializing in Jetpack Compose, Kotlin, and Material3.
Target Application: "LinkShield Sandbox"
Platform: Android (Jetpack Compose, Kotlin, Material3, Coroutines, Navigation Compose, RoleManager)

================================================================================
1. COMPLETE APP SCREEN NAVIGATION TREE & EXECUTION SEQUENCE
================================================================================

[ APP LAUNCH & ONBOARDING GATEKEEPERS ]
├── 1. Disclaimer Screen (No-Checkbox Legal Terms & Policy Consent)
│   └── Trigger: First App Launch -> Navigates to [Enable Shield Screen]
└── 2. Enable Shield Screen (Mandatory Default Browser Setup - Gatekeeper)
    └── Trigger: App set as Default Browser -> Unlocks [Main Navigation Shell]

[ MAIN NAVIGATION SHELL (4-TAB BOTTOM NAVIGATION SYSTEM) ]
├── 3. Main Screen / Shield Screen (Core Browser & Isolated WebView Sandbox)
│   └── Tab 1: [ 🛡️ Shield ] (Default Main Viewport, DNS/WireGuard Controls, Theme Switch)
├── 4. Link Checker Screen (External URL Capture & Safety Analysis Engine)
│   └── Tab 3: [ 🔍 Check ] (Intercepts incoming HTTP/HTTPS links, pre/post analysis workflow)
├── 5. Grabber Screen (Media Sniffer, Preview & Download Engine)
│   └── Tab 2: [ 📥 Grabber ] (Dynamic media capture, free limit vs. Pro unlimited states)
└── 6. Upgrade Screen (Dual-State Pricing & License Activation System)
    └── Tab 4: [ ⭐ Upgrade ] (Rs. 350 PKR / 1.25 USDT Lifetime offer, manual payment logos, activation)

================================================================================
SECTION 1: DISCLAIMER SCREEN
================================================================================
SYSTEM INSTRUCTION: NO-CHECKBOX DISCLAIMER SCREEN (LinkShield Sandbox)

Role: You are a Senior Android UI & Legal Compliance Engineer working in Jetpack Compose.
Task: Implement the Play Store Compliant "Disclaimer & Terms" Onboarding Screen WITHOUT checkboxes, using a single direct action button.

--------------------------------------------------------------------------------
COMPONENT SPECIFICATIONS
--------------------------------------------------------------------------------

Header Section:
- Circular Cropped App Logo (`R.drawable.ic_app_logo`) centered using `Modifier.clip(CircleShape)`.
- Title: "DISCLAIMER & TERMS OF USE".

Scrollable Content Container (Play Store Approved Clauses):
- Includes all 5 core legal clauses:
  1. Media Downloader & Copyright Policy
  2. VPN Service & Encryption Terms
  3. Sandbox Isolation & User Liability
  4. Privacy & Zero Data Logging Policy
  5. Default Browser Routing Consent

Primary Action Button:
- Text: `[ 📜 AGREE & CONTINUE ]`
- Behavior: Directly available upon viewing terms. On click, navigates straight to `Enable Shield Screen` (Default Browser Setup).
- NO Checkboxes / Toggles required.

--------------------------------------------------------------------------------
ASCII VISUAL REFERENCE
--------------------------------------------------------------------------------

┌────────────────────────────────────────────────────────┐
│                        .──────.                        │
│                       /  APP   \                       │
│                      |  LOGO    |                      │
│                       \        /                       │
│                        '──────'                        │
│                                                        │
│            [ 📜 DISCLAIMER & TERMS OF USE ]            │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 1. MEDIA DOWNLOADER & COPYRIGHT POLICY             │ │
│ │ 2. VPN SERVICE & ENCRYPTION TERMS                  │ │
│ │ 3. SANDBOX ISOLATION & USER LIABILITY              │ │
│ │ 4. PRIVACY & ZERO DATA LOGGING                     │ │
│ │ 5. DEFAULT BROWSER ROUTING CONSENT                 │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ [ 📜 AGREE & CONTINUE ]                                │
└────────────────────────────────────────────────────────┘

================================================================================
SECTION 2: ENABLE SHIELD SCREEN
================================================================================
UPDATED SYSTEM INSTRUCTION: ENABLE SHIELD SCREEN (LinkShield Sandbox)

Role: You are a Senior Android UI Engineer working in Jetpack Compose.
Task: Render the Enable Shield Onboarding Screen with a STRICTLY CIRCULAR App Logo.

--------------------------------------------------------------------------------
COMPONENT SPECIFICATIONS
--------------------------------------------------------------------------------

App Logo Container:
- Image Resource: `R.drawable.ic_app_logo`
- Shape Requirement: MUST use `Modifier.clip(CircleShape)` with `ContentScale.Crop`.
- Sizing: Centered circular avatar (e.g., `120.dp` size).
- Border: Optional subtle elevation/accent border around the circle (`BorderStroke(2.dp, PrimaryColor)`).

Text & Action Buttons:
- Title: "SET AS DEFAULT BROWSER"
- Subtext: "To enable LinkShield protection & auto-scan links from other apps, you MUST set LinkShield as your default browser."
- Warning Badge: "🔒 MANDATORY STEP (Cannot be skipped)"
- Primary Button: `[ 🛡️ ENABLE SHIELD & SET AS DEFAULT ]` (Triggers Default Browser RoleManager Intent).

--------------------------------------------------------------------------------
ASCII VISUAL REFERENCE
--------------------------------------------------------------------------------

┌────────────────────────────────────────────────────────┐
│                        .──────.                        │
│                       /  APP   \                       │
│                      |  LOGO    |                      │  ← Circular Cropped Logo
│                       \        /                       │
│                        '──────'                        │
│                                                        │
│               [ SET AS DEFAULT BROWSER ]               │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │  🔒 MANDATORY STEP (Cannot be skipped)             │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ [ 🛡️ ENABLE SHIELD & SET AS DEFAULT ]                  │
└────────────────────────────────────────────────────────┘
================================================================================
SECTION 3: MAIN SCREEN / SHIELD SCREEN
================================================================================
SYSTEM INSTRUCTION: LOCK MAIN BROWSER SCREEN LAYOUT (LinkShield Sandbox)

Role: You are a Senior Android UI Engineer working in Jetpack Compose.
Task: Implement and strictly adhere to the LOCKED Main Browser Screen UI layout for "LinkShield Sandbox".

--------------------------------------------------------------------------------
1. COMPONENT SPECIFICATIONS & ROW BREAKDOWN
--------------------------------------------------------------------------------

Row 1 (Top App Bar - Justified Row):
- Left Side: App Logo (`R.drawable.ic_app_logo`)
- Right Side (Horizontal Row with Spacing):
  1. `[🛡️ Shield 🔻]` -> Dropdown Button for Network Security Settings.
  2. `[☀️ ──◯ 🌙]`     -> Custom Sliding Toggle Switch for Light/Dark Theme.
  3. `Badge Indicator` -> Conditional Badge:
     - Free State: Shows `[⏳ Trial: 30d]` (or remaining trial days).
     - Pro State: Shows `[👑 PRO]` (Gold/Premium highlight).

Row 2 (Browser Navigation & Address Bar):
- Buttons: Back (`[←]`), Forward (`[→]`), Reload (`[↻]`).
- URL Bar: Rounded TextField with SSL Lock Icon (`🔒`), Input URL, and Clear/Action button.

Row 3 (Middle Viewport Container):
- Dynamic Isolated WebView Container (`SandboxWebViewContainer`) occupying all available vertical space.

Row 4 (Bottom Navigation Bar - 4 Equal Tabs):
- Tab 1: `[ 🛡️ Shield ]`  (Browser / Core Sandbox)
- Tab 2: `[ 📥 Grabber ]` (Media Sniffer & Downloader)
- Tab 3: `[ 🔍 Check ]`   (URL Safety & Threat Checker)
- Tab 4: `[ ⭐ Upgrade ]` (Pro Membership & License Activation)

--------------------------------------------------------------------------------
2. DROPDOWN MENU BEHAVIOR: `[🛡️ Shield 🔻]`
--------------------------------------------------------------------------------
When tapped, open a Compose `DropdownMenu` with ONLY two simple options:
- [☑ / ☐] Enable DNS Shield (Auto-connects AdGuard DNS for Ad & Threat Blocking)
- [☑ / ☐] Connect WireGuard (Auto-connects to optimal VPN tunnel)

--------------------------------------------------------------------------------
3. ASCII VISUAL REFERENCE (STRICT REQUIREMENT)
--------------------------------------------------------------------------------

[ FREE TRIAL STATE ]
┌──────┬─────────────────────────────────────────────────────────────────┐
│      │ [🛡️ Shield 🔻]             [☀️ ──◯ 🌙]           [⏳ Trial: 30d] │
│ LOGO │─────────────────────────────────────────────────────────────────┤
│      │ [←] [→] [↻]  [ 🔒 https://example.com/sandbox...            ]   │
├──────┴─────────────────────────────────────────────────────────────────┤
│                                                                        │
│                     [ DYNAMIC TAB CONTENT AREA ]                       │
│                        (Isolated WebView)                              │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]      │
└────────────────────────────────────────────────────────────────────────┘

[ PRO ACTIVATED STATE ]
┌──────┬─────────────────────────────────────────────────────────────────┐
│      │ [🛡️ Shield 🔻]             [☀️ ──◯ 🌙]               [👑 PRO]    │
│ LOGO │─────────────────────────────────────────────────────────────────┤
│      │ [←] [→] [↻]  [ 🔒 https://example.com/sandbox...            ]   │
├──────┴─────────────────────────────────────────────────────────────────┤
│                                                                        │
│                     [ DYNAMIC TAB CONTENT AREA ]                       │
│                        (Isolated WebView)                              │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]      │
└────────────────────────────────────────────────────────────────────────┘

--------------------------------------------------------------------------------
INSTRUCTION FOR AI CODE GENERATION:
Do NOT alter the structural positioning of any element in Row 1, Row 2, or Row 4.
Generate Jetpack Compose code using proper `Row`, `Column`, `Scaffold`, and `NavigationBar` 
while maintaining states for (ThemeSwitch, IsProUser, IsDnsEnabled, IsVpnConnected).

================================================================================
SECTION 4: LINK CHECKER SCREEN
================================================================================
SYSTEM INSTRUCTION: MANDATORY DEFAULT BROWSER & INTERCEPT FLOW (LinkShield Sandbox)

Role: You are a Senior Android UI/System Engineer working in Kotlin & Jetpack Compose.
Task: Implement the MANDATORY Default Browser onboarding gatekeeper, silent background link interception, and dynamic analysis workflow.

--------------------------------------------------------------------------------
1. MANDATORY DEFAULT BROWSER SETUP (NO-SKIP GATEKEEPER)
--------------------------------------------------------------------------------
- App Launch Flow: Disclaimer Screen -> Enable Shield Screen.
- Gatekeeper Restriction: `Enable Shield Screen` MUST act as a non-skippable blocking gatekeeper.
- System Action: Launch `RoleManager` / `ACTION_MANAGE_DEFAULT_APPS` intent to set LinkShield as the Default Browser.
- Navigation Policy: User CANNOT proceed to `Main Screen` until `isDefaultBrowser == true`.

--------------------------------------------------------------------------------
2. BACKGROUND LINK INTERCEPTION ENGINE
--------------------------------------------------------------------------------
- Manifest Setup: Declare `<intent-filter>` for `VIEW`, `BROWSABLE`, `http`, `https` schemes in `AndroidManifest.xml`.
- App Entry Behavior: Runs silently in the background (no floating UI/bubbles). When an external link is clicked in WhatsApp, Chrome, or any app, Android launches LinkShield directly into `Check Tab`.
- URL Parsing: Extract target URL from incoming intent, populate `Check Screen` URL container automatically.

--------------------------------------------------------------------------------
3. CHECK TAB ANALYSIS WORKFLOW
--------------------------------------------------------------------------------
- State 1 (Pre-Analysis):
  - Target URL displayed in non-editable/input bar.
  - Primary Action Button: `[ 🔍 ANALYZE LINK SAFETY ]`.
  - The `[ 🚀 OPEN IN SANDBOX ]` button is HIDDEN or DISABLED until analysis finishes.
- State 2 (Post-Analysis):
  - Threat Report rendered (SSL, Phishing score, Domain Reputation).
  - Primary Action Button Revealed: `[ 🚀 OPEN IN SANDBOX ]`.
  - Tapping `[ 🚀 OPEN IN SANDBOX ]` navigates directly to `Shield Tab` (Isolated WebView) and loads the verified web page.

--------------------------------------------------------------------------------
4. ASCII VISUAL REFERENCE (MANDATORY & INTERCEPT FLOW)
--------------------------------------------------------------------------------

[ MANDATORY ENABLE SHIELD SCREEN ]
┌────────────────────────────────────────────────────────┐
│                      /─────\                           │
│                     |   🛡️   |                         │
│                     | SHIELD|                          │
│                      \─────/                           │
│               [ SET AS DEFAULT BROWSER ]               │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │  🔒 MANDATORY STEP (Cannot be skipped)             │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ [ 🛡️ ENABLE SHIELD & SET AS DEFAULT ]                  │
└────────────────────────────────────────────────────────┘

[ CHECK TAB: POST-ANALYSIS WITH SANDBOX LAUNCH BUTTON ]
┌────────────────────────────────────────────────────────┐
│ [⬅️ Back to Web]              [ 🔍 URL Safety Check ]  │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 🔗 https://example-target-domain.com/login...     │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │  🟢 STATUS: SAFE & CLEAN                           │ │
│ │  Safety Score: 98/100 (No Phishing Detected)       │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ [ 🚀 OPEN IN SANDBOX ]                                 │
├────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]  │
└────────────────────────────────────────────────────────┘
================================================================================
SECTION 5: GRABBER SCREEN
================================================================================
SYSTEM INSTRUCTION: LOCK MEDIA GRABBER SCREEN LAYOUT (LinkShield Sandbox)

Role: You are a Senior Android UI Engineer working in Jetpack Compose.
Task: Implement and strictly adhere to the LOCKED "LinkShield Grabber" Screen UI layout for "LinkShield Sandbox".

--------------------------------------------------------------------------------
1. COMPONENT SPECIFICATIONS & ROW BREAKDOWN
--------------------------------------------------------------------------------

Row 1 (Top Header Bar):
- Left Side: `[⬅️ Back to Web]` Button. Tapping this pops the backstack or switches back to the Browser Tab view instantly.
- Right Side: Title Screen Indicator (`📂 Media Grabber`).

Row 2 (Conditional Status Banner Card):
- IF `isProUser == false` (Trial / Free User):
  - Show Info Alert Card: "🎁 X Free Downloads Remaining (Upgrade to Pro for Unlimited Downloads)"
  - Display remaining download count dynamically.
- IF `isProUser == true` (Pro User):
  - Show Gold/Premium Styled Badge Card: "👑 PRO UNLIMITED ACTIVE - High-Speed Downloads & Full Quality Unlocked"

Row 3 (URL Input & Fetch Container):
- Address Bar TextField: Pre-filled with auto-detected media link from Sandbox Browser, or accepts manually pasted link.

Row 4 (Dynamic Media Preview Card):
- Container showing Thumbnail, Video/Audio Title, Duration, and Estimated File Size once fetched.

Row 5 (Download Options & Format Toggles):
- Checkbox 1: `[☑] Extract Audio Only`
- Checkbox 2: `[☑] Best Quality (HD/4K)`

Row 6 (Primary Action Area):
- Full Width Button: `[ 🚀 Download Media ]`

Row 7 (Bottom Navigation Bar - 4 Equal Tabs):
- Tab 1: `[ 🛡️ Shield ]`
- Tab 2: `[ 📥 Grabber ]` (ACTIVE)
- Tab 3: `[ 🔍 Check ]`
- Tab 4: `[ ⭐ Upgrade ]`

--------------------------------------------------------------------------------
2. ASCII VISUAL REFERENCE (STRICT REQUIREMENT)
--------------------------------------------------------------------------------

[ TRIAL / FREE STATE ]
┌────────────────────────────────────────────────────────┐
│ [⬅️ Back to Web]             [ 📂 Media Grabber ]      │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 🎁 20 Free Downloads Remaining                     │ │
│ │ (Upgrade to Pro for Unlimited Downloads)           │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 🔗 [ Paste or Auto-Fetched Link...             ]   │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│   ┌────────────────────────────────────────────────┐   │
│   │           🎬 MEDIA PREVIEW AREA                │   │
│   └────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────┤
│ Options:                                               │
│ [☑] Extract Audio Only      [☑] Best Quality (HD/4K)   │
│                                                        │
│ [ 🚀 Download Media ]                                  │
├────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]  │
└────────────────────────────────────────────────────────┘

[ PRO ACTIVATED STATE ]
┌────────────────────────────────────────────────────────┐
│ [⬅️ Back to Web]             [ 📂 Media Grabber ]      │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 👑 PRO UNLIMITED ACTIVE                            │ │
│ │ High-Speed Downloads & Full Quality Unlocked       │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 🔗 [ Paste or Auto-Fetched Link...             ]   │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│   ┌────────────────────────────────────────────────┐   │
│   │           🎬 MEDIA PREVIEW AREA                │   │
│   └────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────┤
│ Options:                                               │
│ [☑] Extract Audio Only      [☑] Best Quality (HD/4K)   │
│                                                        │
│ [ 🚀 Download Media ]                                  │
├────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]  │
└────────────────────────────────────────────────────────┘

--------------------------------------------------------------------------------
INSTRUCTION FOR AI CODE GENERATION:
Maintain absolute alignment with `isProUser` state. Ensure correct handling of the 
`Back to Web` navigation action and render the Gold Pro Badge using custom gradient borders.

================================================================================
SECTION 6: UPGRADE SCREEN
================================================================================
SYSTEM INSTRUCTION: DUAL-STATE UPGRADE SCREEN WITH PRICING (LinkShield Sandbox)

Role: You are a Senior Android UI Engineer working in Jetpack Compose.
Task: Implement "LinkShieldUpgradeScreen" using state management (`isProUser: Boolean`).
Render exact UI content, Pricing Tags (Rs. 350 / 1.25 USDT Lifetime), and Brand Badges.

--------------------------------------------------------------------------------
1. COMPONENT & PRICING SPECIFICATIONS
--------------------------------------------------------------------------------

Pricing Structure:
- PKR Price: "Rs. 350 PKR"
- USDT Price: "1.25 USDT"
- Fee Model: "One-Time Payment • Lifetime Access (No Recurring Fee)"

Brand Badge Assets (Stored in repository under `./assets/logos/`):
- EasyPaisa Logo: `./assets/logos/easypaisa.png`
- JazzCash Logo:  `./assets/logos/jazzcash.png`
- USDT TRC20 Logo: `./assets/logos/usdt.png`

--------------------------------------------------------------------------------
STATE 1: TRIAL / FREE USER UI (`isProUser == false`)
--------------------------------------------------------------------------------

Row 1 (Header Bar):
- Left: `[⬅️ Back to Web]` Button
- Right: Title (`⭐ Upgrade Pro`)

Row 2 (Promo Banner Card & Price Highlight):
- Title: "👑 UNLOCK LINKSHIELD PRO"
- Subtitle: "Ultimate Security, Unlimited Media & Peak Speed"
- Price Tag Badge: `[ 🏷️ Rs. 350 PKR  |  1.25 USDT ]`
- Price Subtext: "⚡ One-Time Fee • Lifetime Access"

Row 3 (Play Store Safe Advantages Box):
- `[🚀] Unlimited High-Speed Media Sniffer & Saver`
- `[🛡️] Real-time Smart DNS & Threat Shield`
- `[🔒] Next-Gen Encrypted WireGuard® Tunnel`
- `[⭐] VIP Priority Bandwidth & Pure Ad-Free UX`

Row 4 (Manual Payment Methods Container with 1-Tap Clipboard Copy):
- Section Title: "Pay Rs. 350 PKR or 1.25 USDT (Tap to Copy):"
- Item 1: `<img src="./assets/logos/easypaisa.png" width="24"/>` **EasyPaisa:** `03XX-XXXXXXX`  -> Button `[📋 Copy]`
- Item 2: `<img src="./assets/logos/jazzcash.png" width="24"/>`  **JazzCash:**  `03XX-XXXXXXX`  -> Button `[📋 Copy]`
- Item 3: `<img src="./assets/logos/usdt.png" width="24"/>`      **USDT TRC20:** `TXXXXXXXX...` -> Button `[📋 Copy]`
- Behavior: Tapping `[📋 Copy]` copies the exact string to Clipboard and triggers Toast("Copied to clipboard").

Row 5 (License Key Input & Verification):
- Label: "Enter Activation / License Key:"
- TextField Placeholder: "🔑 Enter License Key or Transaction ID..."
- Action Button: `[ 👑 ACTIVATE PRO LICENSE ]`

Row 6 (Support & Payment Proof Section):
- Label: "💬 For Customer Support & Payment Proof:"
- Action Button: `[ 💬 WhatsApp Support & Send Proof ]`
  (Launches WhatsApp with pre-filled message: "Hello, I sent payment of Rs.350 / 1.25 USDT for LinkShield Pro. Here is my proof:")

--------------------------------------------------------------------------------
STATE 2: PRO ACTIVATED USER UI (`isProUser == true`)
--------------------------------------------------------------------------------

Row 1 (Header Bar):
- Left: `[⬅️ Back to Web]` Button
- Right: Title (`⭐ Pro Status`)

Row 2 (Success Status Banner):
- Title: "👑 YOU ARE A PREMIUM MEMBER"
- Subtitle: "Lifetime License Active on this device."

Row 3 (Account Status Snapshot Container):
- Section Title: "Premium Account Info:"
- `[✔️] Unlimited Media Sniffer & Downloader: ACTIVE`
- `[✔️] Real-time Security & DNS Shield: ACTIVE`
- `[✔️] Encrypted WireGuard® Tunnel: ACTIVE`
- `[📅] License Plan: Lifetime Activated (Rs. 350 / 1.25 USDT)`

Row 4 (Pro VIP Support Section):
- Label: "Need Assistance with your Pro Subscription?"
- Action Button: `[ 💬 Contact Pro Support (WhatsApp) ]`
  (Launches WhatsApp with pre-filled message: "Hello Support, I am a Lifetime Pro Member and need assistance:")

Row 5 (Utility Action):
- Action Button: `[ 🔄 Restore Purchase / Sync License ]`

--------------------------------------------------------------------------------
2. ASCII VISUAL REFERENCES
--------------------------------------------------------------------------------

[ TRIAL / FREE STATE UI WITH LIFETIME PRICING ]
┌────────────────────────────────────────────────────────┐
│ [⬅️ Back to Web]                  [ ⭐ Upgrade Pro ]   │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 👑 UNLOCK LINKSHIELD PRO                           │ │
│ │ Ultimate Security, Unlimited Media & Peak Speed    │ │
│ │                                                    │ │
│ │  🏷️ PRICE: Rs. 350 PKR  |  1.25 USDT               │ │
│ │  ⚡ One-Time Payment • Lifetime Access              │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ Pay Rs. 350 PKR or 1.25 USDT (Tap to Copy):            │
│ ┌────────────────────────────────────────────────────┐ │
│ │ <img src="./assets/logos/easypaisa.png"/> EasyPaisa │ │
│ │ 03XX-XXXXXXX                             [📋 Copy] │ │
│ │ <img src="./assets/logos/jazzcash.png"/>  JazzCash  │ │
│ │ 03XX-XXXXXXX                             [📋 Copy] │ │
│ │ <img src="./assets/logos/usdt.png"/>      USDT TRC │ │
│ │ TXXXXXXXXXX...                           [📋 Copy] │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 🔑 [ Enter License Key or Transaction ID...    ]   │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ [ 👑 ACTIVATE PRO LICENSE ]                            │
│                                                        │
│ 💬 For Customer Support & Payment Proof:               │
│ [ 💬 WhatsApp Support & Send Proof ]                   │
├────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]  │
└────────────────────────────────────────────────────────┘

[ PRO ACTIVATED STATE UI ]
┌────────────────────────────────────────────────────────┐
│ [⬅️ Back to Web]                  [ ⭐ Pro Status ]    │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ 👑 YOU ARE A PREMIUM MEMBER                        │ │
│ │ Lifetime License Active on this device.            │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ Premium Account Info:                                  │
│ ┌────────────────────────────────────────────────────┐ │
│ │ [✔️] Unlimited Media Sniffer & Downloader: ACTIVE   │ │
│ │ [✔️] Real-time Security & DNS Shield: ACTIVE       │ │
│ │ [✔️] Encrypted WireGuard® Tunnel: ACTIVE           │ │
│ │ [📅] License Plan: Lifetime (Rs.350 / 1.25 USDT)   │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ 💬 Need Assistance with your Pro Subscription?         │
│ [ 💬 Contact Pro Support (WhatsApp) ]                 │
│                                                        │
│ [ 🔄 Restore Purchase / Sync License ]                 │
├────────────────────────────────────────────────────────┤
│  [ 🛡️ Shield ]   [ 📥 Grabber ]   [ 🔍 Check ]   [ ⭐ Upgrade ]  │
└────────────────────────────────────────────────────────┘
================================================================================
