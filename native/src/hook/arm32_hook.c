// morvo/native/src/hook/arm32_hook.c
// ARM32 (ARMv7 / Thumb-2) inline hook engine
// Handles both ARM and Thumb mode targets

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include "hook.h"

#define LOG_TAG "Morvo-Hook-ARM32"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ARM32 instruction encoding
#define ARM32_LDR_PC  0xE51FF004  // LDR PC, [PC, #-4]
#define ARM32_BX_LR   0xE12FFF1E  // BX LR (return)
#define ARM32_NOP     0xE320F000  // NOP

// Thumb-2 instruction encoding
#define THUMB_BX_PC   0x4778      // BX PC (switch to ARM mode)
#define THUMB_NOP     0xBF00      // NOP

// Check if address is in Thumb mode (LSB set)
static int is_thumb(void* addr) {
    return ((uintptr_t)addr & 1) != 0;
}

// Install ARM32 (ARM mode) inline hook
static hook_t* arm32_arm_hook(void* target, void* detour) {
    LOGI("ARM32 ARM-mode hook: %p → %p", target, detour);
    
    hook_t* hook = calloc(1, sizeof(hook_t));
    if (!hook) return NULL;
    
    hook->target = target;
    hook->original_size = 8;  // 2 ARM instructions (8 bytes)
    
    hook->original_bytes = malloc(hook->original_size);
    memcpy(hook->original_bytes, target, hook->original_size);
    
    // Allocate trampoline
    hook->trampoline = mmap(NULL, 4096,
        PROT_READ | PROT_WRITE | PROT_EXEC,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    
    if (hook->trampoline == MAP_FAILED) {
        free(hook->original_bytes);
        free(hook);
        return NULL;
    }
    
    // Build trampoline: original + jump back
    uint32_t* tramp = (uint32_t*)hook->trampoline;
    tramp[0] = ((uint32_t*)hook->original_bytes)[0];
    tramp[1] = ((uint32_t*)hook->original_bytes)[1];
    // LDR PC, [PC, #-4] + .word back_addr
    tramp[2] = ARM32_LDR_PC;
    tramp[3] = (uint32_t)((uintptr_t)target + 8);
    
    // Write jump to detour
    uintptr_t page = (uintptr_t)target & ~0xFFF;
    mprotect((void*)page, 4096, PROT_READ | PROT_WRITE | PROT_EXEC);
    
    uint32_t* patch = (uint32_t*)target;
    patch[0] = ARM32_LDR_PC;  // LDR PC, [PC, #-4]
    patch[1] = (uint32_t)detour;  // Address to jump to
    
    __builtin___clear_cache(target, (uint8_t*)target + 8);
    
    LOGI("ARM32 hook installed");
    return hook;
}

// Install ARM32 (Thumb mode) inline hook
static hook_t* arm32_thumb_hook(void* target, void* detour) {
    // Thumb mode: target address has LSB set
    // Real address is target & ~1
    void* real_target = (void*)((uintptr_t)target & ~1);
    
    LOGI("ARM32 Thumb-mode hook: %p → %p", real_target, detour);
    
    hook_t* hook = calloc(1, sizeof(hook_t));
    if (!hook) return NULL;
    
    hook->target = real_target;
    hook->original_size = 8;  // 4 Thumb instructions (8 bytes)
    
    hook->original_bytes = malloc(hook->original_size);
    memcpy(hook->original_bytes, real_target, hook->original_size);
    
    hook->trampoline = mmap(NULL, 4096,
        PROT_READ | PROT_WRITE | PROT_EXEC,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    
    if (hook->trampoline == MAP_FAILED) {
        free(hook->original_bytes);
        free(hook);
        return NULL;
    }
    
    // Build trampoline
    uint16_t* tramp = (uint16_t*)hook->trampoline;
    memcpy(tramp, hook->original_bytes, hook->original_size);
    // BX PC + NOP to switch to ARM mode, then absolute jump
    tramp[4] = THUMB_BX_PC;
    tramp[5] = THUMB_NOP;
    uint32_t* arm_tramp = (uint32_t*)(tramp + 6);
    arm_tramp[0] = ARM32_LDR_PC;
    arm_tramp[1] = (uint32_t)((uint8_t*)real_target + 8 + 1);  // +1 = Thumb
    
    // Write jump to detour (Thumb mode)
    uintptr_t page = (uintptr_t)real_target & ~0xFFF;
    mprotect((void*)page, 4096, PROT_READ | PROT_WRITE | PROT_EXEC);
    
    uint16_t* patch = (uint16_t*)real_target;
    patch[0] = THUMB_BX_PC;   // Switch to ARM mode
    patch[1] = THUMB_NOP;     // Padding
    uint32_t* arm_patch = (uint32_t*)(patch + 2);
    arm_patch[0] = ARM32_LDR_PC;  // LDR PC, [PC, #-4]
    arm_patch[1] = (uint32_t)detour;
    
    __builtin___clear_cache(real_target, (uint8_t*)real_target + 8);
    
    LOGI("ARM32 Thumb hook installed");
    return hook;
}

// Main ARM32 hook dispatcher
hook_t* arm32_inline_hook(void* target, void* detour) {
    if (is_thumb(target)) {
        return arm32_thumb_hook(target, detour);
    } else {
        return arm32_arm_hook(target, detour);
    }
}