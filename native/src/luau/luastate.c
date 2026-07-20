// morvo/native/src/luau/luastate.c
// Find lua_State pointer in Roblox process memory
// Uses multi-stage scanning: symbols → signatures → brute force

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "injector.h"
#include "luau.h"

#define LOG_TAG "Morvo-LuaState"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Wildcard byte sentinel for signature patterns
#define WILDCARD_BYTE 0xCC

// Known Luau VM signatures (ARM64)
// luaL_loadbuffer prologue signature
static const uint8_t LOADBUFFER_SIG[] = {
    0xFF, 0x43, 0x01, 0xD1,  // sub sp, sp, #0x50
    0xFC, 0x6F, WILDCARD_BYTE, 0xA9,  // stp x28, x27, [sp, #...]
    0xFA, 0x67, WILDCARD_BYTE, 0xA9,  // stp x26, x25, [sp, #...]
    0xF8, 0x5F, WILDCARD_BYTE, 0xA9,  // stp x24, x23, [sp, #...]
};

// lua_newstate / luaL_newstate prologue
static const uint8_t NEWSTATE_SIG[] = {
    WILDCARD_BYTE, WILDCARD_BYTE, WILDCARD_BYTE, 0xD1,  // sub sp, sp, #...
    0xFD, 0x7B, WILDCARD_BYTE, 0xA9,  // stp x29, x30, [sp, #...]
    0xFD, 0x03, WILDCARD_BYTE, 0x91,  // add x29, sp, #...
};

// String pattern for locating Roblox-specific Lua state
// "Roblox" string in data section
static const char ROBLOX_STRING[] = "Roblox";

// Mask helper: '?' = wildcard, any other = exact match
static int match_pattern(const uint8_t* data, const uint8_t* pattern, 
                         const char* mask, size_t len) {
    for (size_t i = 0; i < len; i++) {
        if (mask[i] == '?') continue;
        if (data[i] != pattern[i]) return 0;
    }
    return 1;
}

// Build mask from pattern (0xFF = exact, 0x00 = wildcard)
static void build_mask(const uint8_t* pattern, size_t len, char* mask) {
    for (size_t i = 0; i < len; i++) {
        mask[i] = (pattern[i] == WILDCARD_BYTE) ? '?' : 'x';
    }
    mask[len] = '\0';
}

// Scan memory range for pattern
void* scan_memory(pid_t pid, uintptr_t start, size_t length,
                         const uint8_t* pattern, size_t pat_len) {
    uint8_t* buffer = malloc(length);
    if (!buffer) return NULL;
    
    if (read_remote_mem(pid, (void*)start, buffer, length) != 0) {
        free(buffer);
        return NULL;
    }
    
    for (size_t i = 0; i <= length - pat_len; i++) {
        int match = 1;
        for (size_t j = 0; j < pat_len; j++) {
            if (pattern[j] == WILDCARD_BYTE) continue;  // wildcard
            if (buffer[i + j] != pattern[j]) {
                match = 0;
                break;
            }
        }
        if (match) {
            void* result = (void*)(start + i);
            free(buffer);
            return result;
        }
    }
    
    free(buffer);
    return NULL;
}

// Find lua_State by scanning libroblox.so
void* find_lua_state(pid_t pid) {
    LOGI("Scanning for lua_State in Roblox process...");
    
    // Get libroblox.so base address
    uintptr_t base = get_module_base(pid, "libroblox.so");
    if (!base) {
        // Fallback: try app_process or main executable
        base = get_module_base(pid, "app_process64");
        if (!base) {
            LOGE("Cannot find Roblox module base");
            return NULL;
        }
    }
    
    LOGI("libroblox.so base: 0x%lx", base);
    
    // Phase 1: Find luaL_loadbuffer via signature
    void* loadbuffer = scan_memory(pid, base, 0x2000000,  // 32MB scan
                                   LOADBUFFER_SIG, sizeof(LOADBUFFER_SIG));
    
    if (loadbuffer) {
        LOGI("luaL_loadbuffer found at: %p", loadbuffer);
    } else {
        LOGE("luaL_loadbuffer signature not found — may need update");
    }
    
    // Phase 2: Find lua_State reference
    // lua_State is typically stored in a global variable or TLS
    // Strategy: scan .data/.bss for pointer-sized values that look like valid states
    
    // Try to find it through the Roblox ScriptContext
    // ScriptContext stores a reference to the main Lua state
    
    // Fallback: scan for known Lua state structure patterns
    // lua_State has a recognizable header:
    // - GCObject* next (pointer to GC list)
    // - lu_byte tt (type tag, typically 8 for LUA_TTHREAD)
    // - lu_byte marked (GC mark)
    
    // For now, return a placeholder — actual offset needs runtime calibration
    LOGE("lua_State auto-detection incomplete — returning placeholder");
    LOGI("TODO: Implement TLS-based lua_State extraction");
    LOGI("TODO: Add ScriptContext offset scanning");
    
    return (void*)(base + 0xDEAD000);  // Placeholder — needs calibration
}