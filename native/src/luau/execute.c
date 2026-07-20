// morvo/native/src/luau/execute.c
// Lua script execution via ptrace syscall injection

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <android/log.h>
#include "luau.h"
#include "injector.h"

#define LOG_TAG "Morvo-Execute"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ARM64 register indices
#define ARM64_X0 0
#define ARM64_X1 1
#define ARM64_X2 2
#define ARM64_X3 3
#define ARM64_PC 32
#define ARM64_SP 31
#define ARM64_LR 30

// ============================================================
// Trace trap handler — for intercepting function returns
// ============================================================
typedef int (*trace_callback_t)(pid_t pid, struct user_pt_regs* regs, void* ctx);

static int wait_for_trap(pid_t pid) {
    int status;
    waitpid(pid, &status, 0);
    
    if (WIFEXITED(status)) {
        LOGE("Target process exited with status %d", WEXITSTATUS(status));
        return -1;
    }
    
    if (WIFSIGNALED(status)) {
        LOGE("Target process killed by signal %d", WTERMSIG(status));
        return -1;
    }
    
    if (!WIFSTOPPED(status)) {
        LOGE("Unexpected wait status: 0x%x", status);
        return -1;
    }
    
    int sig = WSTOPSIG(status);
    if (sig == SIGTRAP || sig == (SIGTRAP | 0x80)) {
        return 0;  // Our expected trap
    }
    
    // Forward unexpected signals
    ptrace(PTRACE_CONT, pid, NULL, (void*)(long)sig);
    return -1;
}

// ============================================================
// Remote function call via ptrace register manipulation
// ============================================================
static int remote_call(pid_t pid, void* func_addr, 
                       uint64_t x0, uint64_t x1, uint64_t x2, uint64_t x3,
                       struct user_pt_regs* out_regs) {
    
    struct user_pt_regs regs, saved_regs;
    
    // Save current registers
    if (ptrace(PTRACE_GETREGS, pid, NULL, &saved_regs) == -1) {
        LOGE("PTRACE_GETREGS failed: %s", strerror(errno));
        return -1;
    }
    
    // Backup for modifying
    memcpy(&regs, &saved_regs, sizeof(regs));
    
    // Set up ARM64 calling convention
    regs.regs[0] = x0;
    regs.regs[1] = x1;
    regs.regs[2] = x2;
    regs.regs[3] = x3;
    regs.pc = (uint64_t)func_addr;
    regs.lr = 0;  // Return to 0 = our breakpoint trap
    
    if (ptrace(PTRACE_SETREGS, pid, NULL, &regs) == -1) {
        LOGE("PTRACE_SETREGS failed: %s", strerror(errno));
        return -1;
    }
    
    // Execute
    if (ptrace(PTRACE_CONT, pid, NULL, NULL) == -1) {
        LOGE("PTRACE_CONT failed: %s", strerror(errno));
        ptrace(PTRACE_SETREGS, pid, NULL, &saved_regs);
        return -1;
    }
    
    // Wait for the breakpoint (LR=0 triggers SIGSEGV/SIGILL which we catch)
    int status;
    waitpid(pid, &status, 0);
    
    // Get result registers
    if (out_regs) {
        ptrace(PTRACE_GETREGS, pid, NULL, out_regs);
    }
    
    // Restore original context
    ptrace(PTRACE_SETREGS, pid, NULL, &saved_regs);
    
    return 0;
}

// ============================================================
// Allocate string in remote process
// ============================================================
static void* remote_strdup(pid_t pid, const char* str) {
    size_t len = strlen(str) + 1;
    
    // Use mmap syscall injection to allocate memory
    // Syscall: mmap(addr=0, length=len, prot=PROT_READ|PROT_WRITE, 
    //               flags=MAP_PRIVATE|MAP_ANONYMOUS, fd=-1, offset=0)
    
    struct user_pt_regs regs;
    
    // ARM64 mmap syscall number: 222
    remote_call(pid, NULL,  // We'll inject the syscall manually
                0, len, PROT_READ | PROT_WRITE | PROT_EXEC,
                MAP_PRIVATE | MAP_ANONYMOUS);
    
    // For now, use /proc/pid/mem approach as fallback
    // Find a free page in the process address space
    
    // Simplified: allocate via process_vm_writev to a known writable region
    // In practice, use mmap syscall injection through ptrace
    
    LOGE("remote_strdup: mmap syscall injection pending implementation");
    return NULL;
}

// ============================================================
// Public API: Execute Lua script
// ============================================================
char* lua_execute(pid_t pid, void* L, const char* script) {
    LOGI("Executing script (%zu bytes)...", strlen(script));
    
    if (!L) {
        LOGE("lua_State is NULL");
        return strdup("ERROR: lua_State is NULL");
    }
    
    // Step 1: Allocate space for script in target process
    void* script_mem = remote_strdup(pid, script);
    if (!script_mem) {
        LOGE("Failed to allocate remote memory for script");
        return strdup("ERROR: Cannot allocate memory in target process");
    }
    
    // Step 2: Find luaL_loadbuffer address
    // This would be found during the attach phase
    // For now, placeholder
    void* loadbuffer_addr = NULL;  // TODO: resolve during attach
    
    if (!loadbuffer_addr) {
        return strdup("ERROR: luaL_loadbuffer not resolved — run attach first");
    }
    
    // Step 3: Set identity to max level
    int old_id = lua_set_identity(L, 8);
    
    // Step 4: Call luaL_loadbuffer(L, script, len, "=morvo")
    struct user_pt_regs result;
    int ret = remote_call(pid, loadbuffer_addr,
                          (uint64_t)L,                    // x0: lua_State
                          (uint64_t)script_mem,           // x1: script content
                          (uint64_t)strlen(script),       // x2: length
                          (uint64_t)script_mem,           // x3: chunk name
                          &result);
    
    if (ret != 0) {
        lua_set_identity(L, old_id);
        return strdup("ERROR: luaL_loadbuffer call failed");
    }
    
    // Check return value (x0 should be 0 = LUA_OK)
    if (result.regs[0] != 0) {
        // Error: x1 points to error string on Lua stack
        char err_buf[512];
        read_remote_mem(pid, (void*)result.regs[1], err_buf, sizeof(err_buf) - 1);
        err_buf[sizeof(err_buf) - 1] = '\0';
        
        lua_set_identity(L, old_id);
        
        char* full_err = malloc(strlen(err_buf) + 64);
        sprintf(full_err, "ERROR: Lua compile failed: %s", err_buf);
        return full_err;
    }
    
    // Step 5: Call lua_pcall(L, 0, 0, 0) — execute the loaded chunk
    // TODO: Find lua_pcall address
    void* pcall_addr = NULL;
    
    if (!pcall_addr) {
        lua_set_identity(L, old_id);
        return strdup("OK — chunk loaded but lua_pcall not yet implemented");
    }
    
    ret = remote_call(pid, pcall_addr,
                      (uint64_t)L, 0, 0, 0, &result);
    
    // Step 6: Restore identity
    lua_set_identity(L, old_id);
    
    if (ret != 0 || result.regs[0] != 0) {
        return strdup("ERROR: lua_pcall failed");
    }
    
    LOGI("Script executed successfully");
    return strdup("OK — script executed");
}
