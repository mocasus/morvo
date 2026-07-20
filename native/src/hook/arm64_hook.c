// morvo/native/src/hook/arm64_hook.c
// ARM64 (AArch64) inline hook engine
// Technique: branch-to-register trampoline near target
// Max jump range: ±128MB (B instruction limitation)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include "hook.h"

#define LOG_TAG "Morvo-Hook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ARM64 instruction encoding
#define ARM64_B_INSTR     0x14000000  // B (unconditional branch)
#define ARM64_BL_INSTR    0x94000000  // BL (branch with link)
#define ARM64_LDR_X16     0x58000050  // LDR X16, #8
#define ARM64_BR_X16      0xD61F0200  // BR X16
#define ARM64_NOP          0xD503201F  // NOP

// Page size
#define PAGE_SIZE 4096

// ============================================================
// Allocate trampoline page near target address
// Must be within ±128MB for B instruction
// ============================================================
static void* allocate_trampoline(void* near_addr, size_t size) {
    uintptr_t page = ((uintptr_t)near_addr) & ~(PAGE_SIZE - 1);
    size = (size + PAGE_SIZE - 1) & ~(PAGE_SIZE - 1);
    
    // Search in both directions for a free page within 128MB
    int64_t max_offset = 128 * 1024 * 1024;  // 128MB
    int page_step = (int)PAGE_SIZE;
    
    for (int dir = 0; dir < 2; dir++) {
        int step = dir ? -page_step : page_step;
        int max_pages = max_offset / PAGE_SIZE;
        
        for (int i = 0; i < max_pages; i++) {
            uintptr_t addr = page + (i * step);
            
            void* result = mmap((void*)addr, size,
                PROT_READ | PROT_WRITE | PROT_EXEC,
                MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
            
            if (result == MAP_FAILED) continue;
            
            // Verify jump distance
            int64_t offset = (int64_t)result - (int64_t)near_addr;
            if (offset >= -max_offset && offset < max_offset) {
                LOGI("Trampoline allocated at %p (offset: %+ld)",
                     result, (long)offset);
                return result;
            }
            
            munmap(result, size);
        }
    }
    
    LOGE("Could not find free page within 128MB of %p", near_addr);
    return NULL;
}

// ============================================================
// Calculate B instruction offset
// ============================================================
static uint32_t arm64_b_instruction(void* from, void* to) {
    int64_t offset = ((int64_t)to - (int64_t)from) >> 2;
    
    if (offset < -33554432 || offset >= 33554432) {
        LOGE("Jump out of range: %p → %p (offset: %ld > 128MB)",
             from, to, (long)offset);
        return 0;
    }
    
    return ARM64_B_INSTR | (offset & 0x3FFFFFF);
}

// ============================================================
// Count how many instructions we need to relocate
// We need at least 4 instructions (16 bytes) for the jump
// ============================================================
static size_t count_instructions_to_relocate(uint32_t* code) {
    // ARM64 instructions are always 4 bytes each
    // We need to move at least 4 instructions (the jump takes 4 insns)
    // But we need to avoid cutting in the middle of:
    // - ADRP + ADD pairs (PC-relative address loading)
    // - CBZ/CBNZ (conditional branches that may be short-range)
    
    size_t count = 4;  // Minimum: 4 instructions
    
    // Count additional instructions if 4th is part of a pair
    uint32_t insn4 = code[3];
    
    // Check if instruction 4 is ADRP (bit 31-24 = 1xx1 0000)
    if ((insn4 & 0x9F000000) == 0x90000000) {
        count = 6;  // ADRP + ADD + 4 more
    }
    
    // Check for literal pool references near the hook point
    for (size_t i = 4; i < 8; i++) {
        uint32_t insn = code[i];
        // LDR literal (bit 31-24 = 0001 1000)
        if ((insn & 0x3B000000) == 0x18000000) {
            count = i + 2;  // Include the literal pool
        }
    }
    
    return count * 4;  // Return bytes
}

// ============================================================
// Install ARM64 inline hook
// ============================================================
hook_t* arm64_inline_hook(void* target, void* detour) {
    if (!target || !detour) {
        LOGE("NULL target or detour");
        return NULL;
    }
    
    LOGI("Installing ARM64 hook: %p → %p", target, detour);
    
    hook_t* hook = calloc(1, sizeof(hook_t));
    if (!hook) return NULL;
    
    hook->target = target;
    
    // Determine how many bytes to relocate
    uint32_t* code = (uint32_t*)target;
    hook->original_size = count_instructions_to_relocate(code);
    
    LOGI("Relocating %zu bytes (%zu instructions)",
         hook->original_size, hook->original_size / 4);
    
    // Save original instructions
    hook->original_bytes = malloc(hook->original_size);
    if (!hook->original_bytes) {
        free(hook);
        return NULL;
    }
    memcpy(hook->original_bytes, target, hook->original_size);
    
    // Allocate trampoline
    hook->trampoline = allocate_trampoline(target, PAGE_SIZE);
    if (!hook->trampoline) {
        free(hook->original_bytes);
        free(hook);
        return NULL;
    }
    
    // Build trampoline:
    // [original_instructions] [b back_to_target+original_size]
    uint8_t* tramp = (uint8_t*)hook->trampoline;
    
    // Copy original instructions
    memcpy(tramp, hook->original_bytes, hook->original_size);
    
    // Add jump back to code after the hook
    void* return_address = (uint8_t*)target + hook->original_size;
    uint32_t* jump_slot = (uint32_t*)(tramp + hook->original_size);
    
    // Use LDR X16 + BR X16 for absolute jump (no range limit)
    jump_slot[0] = ARM64_LDR_X16;
    jump_slot[1] = ARM64_BR_X16;
    jump_slot[2] = (uint32_t)((uintptr_t)return_address & 0xFFFFFFFF);
    jump_slot[3] = (uint32_t)(((uintptr_t)return_address >> 32) & 0xFFFFFFFF);
    
    // ============================================================
    // Build jump at target to detour
    // ============================================================
    
    // Make target page writable
    uintptr_t page_start = (uintptr_t)target & ~(PAGE_SIZE - 1);
    if (mprotect((void*)page_start, PAGE_SIZE * 2, 
                 PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect failed: %s", strerror(errno));
        munmap(hook->trampoline, PAGE_SIZE);
        free(hook->original_bytes);
        free(hook);
        return NULL;
    }
    
    // Write the absolute jump — 4 instructions
    // LDR X16, #8    ; Load address from literal pool
    // BR X16         ; Jump to it
    // .quad detour    ; (encoded as two 32-bit halves)
    uint32_t* patch = (uint32_t*)target;
    uint64_t detour_addr = (uint64_t)detour;
    
    patch[0] = ARM64_LDR_X16;                  // LDR X16, #8
    patch[1] = ARM64_BR_X16;                    // BR X16
    patch[2] = (uint32_t)(detour_addr & 0xFFFFFFFF);
    patch[3] = (uint32_t)((detour_addr >> 32) & 0xFFFFFFFF);
    
    // Fill remaining slots with NOP if we relocated more than 4 instructions
    for (size_t i = 4; i < hook->original_size / 4; i++) {
        patch[i] = ARM64_NOP;
    }
    
    // Cache flush — critical for ARM
    __builtin___clear_cache(target, (uint8_t*)target + hook->original_size);
    
    LOGI("✅ ARM64 hook installed: %p → %p", target, detour);
    LOGI("   Trampoline: %p", hook->trampoline);
    
    return hook;
}

// ============================================================
// Remove hook
// ============================================================
void unhook(hook_t* hook) {
    if (!hook) return;
    
    LOGI("Removing hook at %p", hook->target);
    
    // Make target writable
    uintptr_t page_start = (uintptr_t)hook->target & ~(PAGE_SIZE - 1);
    mprotect((void*)page_start, PAGE_SIZE * 2,
             PROT_READ | PROT_WRITE | PROT_EXEC);
    
    // Restore original instructions
    memcpy(hook->target, hook->original_bytes, hook->original_size);
    
    // Cache flush
    __builtin___clear_cache(hook->target, 
                           (uint8_t*)hook->target + hook->original_size);
    
    // Free resources
    munmap(hook->trampoline, PAGE_SIZE);
    free(hook->original_bytes);
    free(hook);
    
    LOGI("Hook removed");
}

// ============================================================
// Get trampoline (for calling original function)
// ============================================================
void* get_trampoline(hook_t* hook) {
    return hook ? hook->trampoline : NULL;
}
