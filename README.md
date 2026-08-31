# 📱 Telecom Widget

A modern, privacy-focused Android application and Home Screen / Lock Screen Widget for tracking live telecom balance (Internet data, call minutes, SMS, and solde) for Moroccan operators (**Maroc Telecom**, **Orange Maroc**, and **inwi**).

---

## ✨ Features

- **⚡ Real-Time Android 16 Live Activity**: Dynamic Now Bar / status bar chip with remaining data indicators and built-in interactive refresh.
- **🎨 Multi-Size Home Screen Widgets**: Glance-powered widgets in 2x1, 3x1, 4x1, 2x2, 4x2, and 4x4 responsive layouts.
- **🔄 Smart Sync Engine**:
  - 5-minute background pulse (`AlarmManager`).
  - Screen unlock auto-refresh (`ACTION_USER_PRESENT`).
  - Network switch trigger (auto-sync when transitioning from Wi-Fi to 4G/5G Cellular data).
  - 3-minute anti-spam throttle protecting against operator rate limits.
- **🔒 100% On-Device & Private**: Credentials and session cookies are encrypted locally on the device using Android DataStore and never sent to any third-party or intermediate servers.
- **📱 Material 3 Expressive UI**: Fluid shape morphing, animated springs, multi-account switcher, and customizable widget themes.

---

## 🛠️ CLI & Reverse-Engineering Scripts

- `iam_client.py` — Maroc Telecom (IAM) Selfcare client
- `orange_client.py` — Orange Maroc Espace Client
- `test_inwi.py` — inwi customer portal integration
- `get_consumption.py` — Multi-operator CLI balance viewer
- `deploy_local.py` — Fast local Gradle build & wireless ADB deployment tool

---

## ⚖️ Disclaimer

*This application is an independent, open-source third-party tool and is not affiliated with, authorized, maintained, sponsored, or endorsed by Maroc Telecom (IAM), Orange Maroc, or inwi. All product and company names are trademarks™ or registered® trademarks of their respective holders.*
