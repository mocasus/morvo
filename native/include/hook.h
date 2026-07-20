// morvo/native/include/hook.h
#ifndef MORVO_HOOK_H
#define MORVO_HOOK_H

#include <stdint.h>
#include <stddef.h>

// Hook context structure
typedef struct {
    void* target;           // Address being hooked
    void* trampoline;       // Trampoline for calling original
    void* original_bytes;   // Saved original instructions
    size_t original_size;   // Size of saved bytes
} hook_t;

// Install ARM64 inline hook
// Replaces first instructions at target with jump to detour
// Returns hook context on success, NULL on failure
hook_t* arm64_inline_hook(void* target, void* detour);

// Install ARM32 inline hook
hook_t* arm32_inline_hook(void* target, void* detour);

// Remove hook and restore original instructions
void unhook(hook_t* hook);

// Get trampoline address (for calling original function)
void* get_trampoline(hook_t* hook);

#endif // MORVO_HOOK_H
