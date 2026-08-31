<div align="center">

# Telecom Widget

<p align="center">
  <strong>Native Mobile Balances & Real-Time Live Widgets for Moroccan Telecom Operators</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-iOS%2017%2B%20%7C%20Android%2012%2B-blue?style=for-the-badge&logo=apple&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Architecture-100%25%20Bespoke%20Native-darkgreen?style=for-the-badge&logo=swift&logoColor=white" alt="Native Architecture" />
  <img src="https://img.shields.io/badge/Security-On--Device%20Enclave-red?style=for-the-badge&logo=auth0&logoColor=white" alt="Security" />
  <img src="https://img.shields.io/badge/Origin-Morocco%20%E2%80%A2%20%D8%A7%D9%84%D9%85%D8%BA%D8%B1%D8%A8-c1272d?style=for-the-badge" alt="Morocco" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Maroc%20Telecom-0A84FF?style=flat-square" alt="Maroc Telecom" />
  <img src="https://img.shields.io/badge/Orange%20Maroc-FF7900?style=flat-square" alt="Orange Maroc" />
  <img src="https://img.shields.io/badge/inwi-D6006E?style=flat-square" alt="inwi" />
  <img src="https://img.shields.io/badge/Languages-EN%20%7C%20FR%20%7C%20AR%20%7C%20%E2%B5%9C%E2%B5%89%E2%B5%8D%E2%B5%89%E2%B5%8F%E2%B5%8E%E2%B5%96-gray?style=flat-square" alt="Localization" />
</p>

</div>

---

## Vision & Heritage

Telecom Widget is engineered by an independent Moroccan software craftsman for the people of Morocco. It represents a determined commitment toward raising the domestic standard of software engineering—demonstrating that utilities designed for Moroccan telecom subscribers can achieve world-class polish, uncompromising security, and bespoke platform parity without commercial tracking or third-party cloud intermediaries.

Rather than taking the conventional shortcut of wrapping a generic web view or deploying a lowest-common-denominator cross-platform abstraction, Telecom Widget was built from the ground up as **two independent native software architectures**. Each operating system received hundreds of hours of dedicated reverse-engineering, tailor-made cryptography, and native design enforcement to make the application feel like a first-party component of iOS and Android alike.

---

## Core Principles & Engineering Standards

### 1. Dual-Architecture Native Engineering
* **No Shared UI Shortcuts**: Every button, animation curve, sheet, widget, and background sync worker was written specifically for the target operating system.
* **Platform-Idiomatic Design**: Adheres strictly to **Apple Human Interface Guidelines (HIG)** with Liquid Glass materials on iOS, and **Samsung One UI 8.5 & Material 3 Expressive** on Android.
* **True Native Widgets**: Powered exclusively by native system runtimes (**WidgetKit / ActivityKit** on iOS and **Jetpack Glance / RemoteViews** on Android).

### 2. Zero-Trust Local Security Model
* **100% On-Device Operation**: The application connects directly from your phone to your carrier's official HTTPS selfcare servers (`iam.ma`, `orange.ma`, `inwi.ma`).
* **Zero Intermediate Servers**: No proxy server, backend API, analytics collector, or remote relay sits between your device and your mobile operator.
* **Hardware-Backed Encryption**: Credentials and tokens are encrypted locally using the **iOS Keychain / Secure Enclave** and **Android Keystore (AES-256-GCM / EncryptedSharedPreferences)**.
* **Biometric App Lock**: Optional instant biometrics protection (Face ID, Touch ID, or Android BiometricPrompt) on every launch.

### 3. Full Moroccan Quad-Language Parity
Both the iOS and Android applications support four fully localized languages with native typography and layout mirroring:
* **English (EN)**
* **French (FR)**
* **Arabic (AR)** — Full Right-to-Left (RTL) interface mirroring
* **Standard Moroccan Tamazight (Tifinagh: ⵜⵉⴼⵉⵏⴰⵖ)**

---

## iOS Architecture

The iOS application combines React Native core state management with a custom Swift bridge and a standalone **WidgetKit & ActivityKit Extension Target**.

```
ios_app/
├── src/                          # React Native Core Architecture
│   ├── services/                 # Carrier Clients (IAM, Orange, Inwi) & Live Activity Bridge
│   ├── screens/                  # Liquid Glass HIG Dashboard & Account Manager
│   └── utils/                    # Biometric Auth & Quad-Language i18n
├── plugins/
│   └── withTelecomWidgets.js     # Continuous Native Generation (CNG) Config Plugin
└── targets/
    ├── native_modules/           # Swift Native Module Bridge (TelecomActivityModule)
    └── widgets/                  # Standalone Native Apple Target (WidgetKit / ActivityKit)
        ├── TelecomWidgetBundle.swift
        ├── TelecomHomeScreenWidget.swift
        ├── TelecomLiveActivityWidget.swift
        ├── TelecomWidgetAttributes.swift
        └── TelecomAppIntent.swift
```

### iOS Features
* **Dynamic Island & Lock Screen Live Activities**:
  - Compact Leading & Trailing Island chips displaying carrier badge and live data balance.
  - Expanded Island view with independent visual gauges for High-Speed Internet and Voice Calls.
  - Minimal circular glyph representation for active multi-island multitasking.
* **WidgetKit Home Screen Widgets**:
  - Available in **Small (2x2)**, **Medium (4x2)**, and **Large (4x4)** form factors.
  - Interactive quick-refresh button powered by `AppIntent` without launching the app.
  - Native iOS widget editing sheet (`WidgetConfigurationIntent`) allowing users to assign specific SIM lines per widget instance.
* **Apple Human Interface Guidelines (HIG)**:
  - Frosted blur materials (`UIBlurEffect`), system SF Symbols, and dynamic typography scaling.
  - Monospaced numeric alignment for clock-like precision.

---

## Android Architecture

The Android application is written natively in **Kotlin** and **Jetpack Compose**, integrating **Material 3 Expressive** and **Samsung One UI design components**.

```
android_app/
├── app/src/main/
│   ├── java/com/telecom/widget/
│   │   ├── MainActivity.kt               # Jetpack Compose UI & Multi-Account Navigation
│   │   ├── WidgetConfigActivity.kt       # One UI Widget Preference Configuration
│   │   ├── glance/
│   │   │   └── ConsumptionWidget.kt      # Jetpack Glance Responsive AppWidgets
│   │   ├── notification/
│   │   │   └── TelecomLiveNotificationHelper.kt  # Android 16 Status Pill & Live Notification
│   │   ├── network/                      # Direct Carrier Scraping & Session Clients
│   │   └── security/                     # Android Keystore & AES-256-GCM Secure Vault
│   ├── res/
│   │   ├── values/                       # English strings & tokens
│   │   ├── values-fr/                    # French strings
│   │   ├── values-ar/                    # Arabic RTL strings
│   │   └── values-b+zgh/                 # Standard Moroccan Amazigh (Tifinagh)
│   └── AndroidManifest.xml
```

### Android Features
* **Android 16 & One UI 8.5 Promoted Live Status Notification**:
  - Live persistent status pill chip in the status bar (`requestPromotedOngoing`).
  - Lock Screen live consumption card with active progress bars and instant refresh action.
* **Jetpack Glance Multi-Layout Widgets**:
  - Multi-form widgets supporting **2x1 Strip**, **2x2 Square**, **4x2 Dashboard**, and **4x4 Full Page** grids.
  - Configurable opacity levels, wallpaper tinting, and typography selection (One UI Sans vs. Google Sans Flex).
* **Smart Background Sync Engine**:
  - Intelligent pulse synchronizer triggered by network handover (Wi-Fi to Cellular data) and device unlock (`ACTION_USER_PRESENT`).
  - Built-in rate-limit protection preventing carrier account lockouts.

---

## Operator Compatibility & Technical Support

| Operator | Authentication Mode | Multi-Line Support | Metrics Tracked |
| :--- | :--- | :---: | :--- |
| **Maroc Telecom (IAM)** | Phone Number + Password | Yes | Internet Data, Voice Calls, National/International Promos, Main Balance |
| **Orange Maroc** | Phone / Email + Password | Yes | 4G/5G Internet, Voice Minutes, Extra Recharge Solde, Active Pass |
| **inwi** | Phone Number + Password | Yes | Data Volume, Voice Units, Inwi Club / Roaming Balances |

---

## Local Security & Privacy Architecture

```
[ Your Device ]
  │
  ├── [ Hardware Keystore / Secure Enclave ] ── (Encrypted Credentials Vault)
  │
  ├── [ Biometric Layer ] ──────────────────── (Face ID / Fingerprint Verification)
  │
  └── [ Direct TLS 1.3 Socket ]
        │
        ├─── Direct HTTPS ───> [ Maroc Telecom Selfcare ]
        ├─── Direct HTTPS ───> [ Orange Maroc Portal ]
        └─── Direct HTTPS ───> [ inwi Customer API ]
```

* **No Data Collection**: There are no diagnostic trackers, Google Analytics, Firebase telemetry, or remote logging libraries bundled in either application.
* **Ephemeral Sessions**: Session cookies and Bearer tokens are kept in volatile memory and re-authenticated on-demand.

---

## CLI & Reverse Engineering Tools

The root repository contains standalone Python automation scripts used to inspect, reverse-engineer, and verify operator API payloads:

* `get_consumption.py` — Terminal-based multi-carrier balance viewer.
* `iam_client.py` — Maroc Telecom Selfcare REST client.
* `orange_client.py` — Orange Maroc Espace Client API integration.
* `dump_android.py` & `dump_android_app.cmd` — Single-file compiler for Android source review.

---

## Disclaimer

This software is an independent, community-driven open-source project. It is not affiliated with, authorized, maintained, sponsored, or endorsed by Maroc Telecom (Itissalat Al-Maghrib), Orange Maroc (Médi Telecom), or inwi (Wana Corporate). All operator trademarks, service marks, and brand names belong strictly to their respective owners.

---

<div align="center">
  <sub>Built with pride in Morocco for Moroccan mobile users.</sub>
</div>
