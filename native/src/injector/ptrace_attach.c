// morvo/native/src/injector/ptrace_attach.c
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <errno.h>
#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "Morvo-Injector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

pid_t find_roblox_pid(void) {
    DIR* proc = opendir("/proc");
    if (!proc) {
        LOGE("Cannot open /proc — need root?");
        return -1;
    }
    
    struct dirent* entry;
    while ((entry = readdir(proc)) != NULL) {
        if (entry->d_type != DT_DIR) continue;
        
        // Skip non-numeric directory names (not processes)
        char* endptr;
        pid_t pid = strtol(entry->d_name, &endptr, 10);
        if (*endptr != '\0') continue;
        
        char cmdline_path[256];
        snprintf(cmdline_path, sizeof(cmdline_path), 
                 "/proc/%d/cmdline", pid);
        
        FILE* fp = fopen(cmdline_path, "r");
        if (!fp) continue;
        
        char cmdline[512] = {0};
        size_t len = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
        fclose(fp);
        
        if (len == 0) continue;
        
        // Check for Roblox package name
        if (strstr(cmdline, "com.roblox.client")) {
            closedir(proc);
            LOGI("Found Roblox: PID=%d", pid);
            return pid;
        }
    }
    
    closedir(proc);
    LOGE("Roblox process not found");
    return -1;
}

int ptrace_attach(pid_t pid) {
    LOGI("Attaching to PID %d...", pid);
    
    if (ptrace(PTRACE_ATTACH, pid, NULL, NULL) == -1) {
        LOGE("PTRACE_ATTACH failed: %s", strerror(errno));
        return -1;
    }
    
    int status;
    waitpid(pid, &status, 0);
    
    if (!WIFSTOPPED(status)) {
        LOGE("Process did not stop after attach");
        ptrace(PTRACE_DETACH, pid, NULL, NULL);
        return -1;
    }
    
    // Enable useful ptrace options
    ptrace(PTRACE_SETOPTIONS, pid, NULL, 
           PTRACE_O_TRACESYSGOOD | PTRACE_O_TRACEEXIT);
    
    LOGI("Attached to PID %d", pid);
    return 0;
}

int ptrace_detach(pid_t pid) {
    LOGI("Detaching from PID %d...", pid);
    
    if (ptrace(PTRACE_DETACH, pid, NULL, NULL) == -1) {
        LOGE("PTRACE_DETACH failed: %s", strerror(errno));
        return -1;
    }
    
    LOGI("Detached");
    return 0;
}

void* allocate_remote_mem(pid_t pid, size_t size) {
    // Use mmap via ptrace syscall injection
    // For now, use /proc/pid/mem with MAP_SHARED workaround
    // Production: inject mmap syscall
    
    LOGE("allocate_remote_mem: not yet implemented");
    return NULL;
}

int write_remote_mem(pid_t pid, void* remote_addr, const void* data, size_t size) {
    // Use process_vm_writev (requires Linux 3.2+)
    struct iovec local = { (void*)data, size };
    struct iovec remote = { remote_addr, size };
    
    ssize_t written = process_vm_writev(pid, &local, 1, &remote, 1, 0);
    if (written < 0) {
        LOGE("process_vm_writev failed: %s", strerror(errno));
        return -1;
    }
    
    return (written == (ssize_t)size) ? 0 : -1;
}

int read_remote_mem(pid_t pid, void* remote_addr, void* buffer, size_t size) {
    struct iovec local = { buffer, size };
    struct iovec remote = { remote_addr, size };
    
    ssize_t read_bytes = process_vm_readv(pid, &local, 1, &remote, 1, 0);
    if (read_bytes < 0) {
        LOGE("process_vm_readv failed: %s", strerror(errno));
        return -1;
    }
    
    return (read_bytes == (ssize_t)size) ? 0 : -1;
}