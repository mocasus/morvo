# Morvo — Custom Android Roblox Executor

## Core Engine
- **Platform:** Android (ARM64-v8a, armeabi-v7a)
- **Language:** Kotlin (UI) + C (Native) + Lua (Payload)
- **Min SDK:** 28 (Android 9+)
- **NDK Version:** 26+

## Project Structure
```
morvo/
├── app/src/main/java/com/morvo/    # Android App (Kotlin)
│   ├── MainActivity.kt             # Entry point
│   ├── ui/                         # UI components
│   │   ├── ScriptEditor.kt         # Code editor
│   │   ├── FloatingMenu.kt         # In-game overlay
│   │   └── ScriptHub.kt            # Script browser
│   ├── injector/
│   │   ├── InjectorBridge.kt       # JNI bridge
│   │   └── ProcessManager.kt       # Roblox process monitor
│   ├── network/
│   │   ├── ScriptRepo.kt           # Fetch scripts from server
│   │   └── LicenseAPI.kt           # Key validation
│   └── utils/
│       ├── RootCheck.kt            # Root detection
│       └── DeviceFingerprint.kt    # Anti-ban device ID
├── native/                         # Native injector (C + ARM asm)
│   ├── CMakeLists.txt
│   ├── include/
│   │   ├── injector.h
│   │   ├── luau.h
│   │   └── hook.h
│   └── src/
│       ├── main.c                  # JNI_OnLoad
│       ├── injector/
│       │   ├── ptrace_attach.c     # Process attachment
│       │   ├── mem_scan.c          # Memory pattern scanner
│       │   └── proc_maps.c         # /proc/pid/maps parser
│       ├── luau/
│       │   ├── luastate.c          # lua_State acquisition
│       │   ├── execute.c           # Script execution
│       │   └── identity.c          # Identity spoofing
│       ├── hook/
│       │   ├── arm64_hook.c        # ARM64 inline hook
│       │   └── arm32_hook.c        # ARMv7 inline hook
│       └── bypass/
│           ├── hyperion.c          # Hyperion evasion
│           └── integrity.c         # Integrity check bypass
├── server/api/                     # Backend (Python FastAPI)
│   ├── main.py                     # API server
│   ├── routes/
│   │   ├── auth.py                 # Key validation
│   │   └── scripts.py              # Script distribution
│   └── models/database.py          # SQLite/PostgreSQL
└── scripts/                        # Lua payloads
    ├── universal/
    │   ├── esp.lua
    │   ├── aimbot.lua
    │   └── autofarm.lua
    └── games/
        ├── bloxfruits.lua
        ├── bladball.lua
        └── arsenal.lua
```

## Build Requirements
```bash
# Android Studio Hedgehog+
# NDK 26.1.10909125
# CMake 3.22+
# Kotlin 2.0+
```

## Roadmap
- [ ] Week 1-2: Dev env + dummy injector (ptrace attach)
- [ ] Week 3-4: Luau VM research + lua_State scan
- [ ] Week 5-6: ARM64 hook engine + Hyperion bypass
- [ ] Week 7-8: Full execution pipeline + UI
- [ ] Week 9-10: Key system + backend
- [ ] Week 11-12: Testing + script hub
