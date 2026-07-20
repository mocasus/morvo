<p align="center">
  <h1 align="center">⚡ Morvo</h1>
  <p align="center">Custom Android Roblox Executor — Built from scratch</p>
  <p align="center">
    <img src="https://img.shields.io/badge/version-v0.1.0-181717?style=flat-square" alt="Version">
    <img src="https://img.shields.io/badge/status-dev-red?style=flat-square" alt="Status">
    <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android" alt="Platform">
    <img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" alt="License">
  </p>
</p>

---

## ✨ Features

- 🎯 **Native ARM64 Injection** — ptrace-based process attachment
- 🔧 **Luau VM Manipulation** — Full access to Roblox's Lua VM
- 🛡️ **Hyperion Bypass** — Kernel-level anti-cheat evasion
- 📱 **Floating Overlay Menu** — In-game script execution UI
- 🔑 **Multi-Device Key System** — License validation via backend API
- 📦 **Script Hub** — Auto-load scripts for popular games

## 🏗️ Architecture

```
Morvo APK (Kotlin/Java)
  └─ JNI Bridge
       └─ Native Injector (.so)
            ├─ ptrace attach → Roblox process
            ├─ ARM64 inline hook → luaL_loadbuffer
            └─ Execute arbitrary Lua via Luau VM
```

## 🚀 Quick Start

```bash
# Clone repo
git clone https://github.com/mocasus/morvo.git
cd morvo

# Open in Android Studio
# Build → NDK 26+ required
# Run on ARM64 device (Android 9+)
```

## ⚠️ Disclaimer

This project is for **research purposes only**. Use only on games you own or have explicit permission to test. The author is not responsible for any misuse.

<sub>v0.1.0 · 2025 · Built by <a href="https://github.com/mocasus">@mocasus</a></sub>
