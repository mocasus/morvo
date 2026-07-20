<p align="center">
  <h1 align="center">⚡ Morvo</h1>
  <p align="center">Custom Android Roblox Executor — Built from scratch</p>
  <p align="center">
    <a href="https://github.com/mocasus/morvo/releases/latest/download/morvo-exe.apk">
      <img src="https://img.shields.io/badge/DOWNLOAD-NOW-2ea44f?style=for-the-badge&logo=android" alt="Download Now">
    </a>
    <br>
    <img src="https://img.shields.io/badge/version-v0.5.0-181717?style=flat-square" alt="Version">
    <img src="https://img.shields.io/badge/status-beta-yellow?style=flat-square" alt="Status">
    <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android" alt="Platform">
    <img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" alt="License">
  </p>
</p>

---

## 📥 Download

**[⬇ Download morvo-exe.apk (v0.5.0)](https://github.com/mocasus/morvo/releases/latest/download/morvo-exe.apk)**

> Enable **Unknown Sources** in Settings → Install APK → Open Morvo

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
git clone https://github.com/mocasus/morvo.git
cd morvo
# Open in Android Studio → Build → Run
```

## 🔗 Links

- **Dashboard**: [morvo-dashboard.vercel.app](https://morvo-dashboard.vercel.app)
- **API**: `43.134.131.85/morvo`
- **Releases**: [github.com/mocasus/morvo/releases](https://github.com/mocasus/morvo/releases)

## ⚠️ Disclaimer

This project is for **research purposes only**. Use only on games you own or have explicit permission to test.

<sub>v0.5.0 · 2025 · Built by <a href="https://github.com/mocasus">@mocasus</a></sub>