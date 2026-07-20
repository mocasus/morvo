// morvo/native/include/injector.h
#ifndef MORVO_INJECTOR_H
#define MORVO_INJECTOR_H

#include <sys/types.h>
#include <stdint.h>

// Roblox package identifier
#define ROBLOX_PACKAGE "com.roblox.client"

// Find Roblox process PID
// Returns PID on success, -1 if not found
pid_t find_roblox_pid(void);

// Attach to process via ptrace
// Returns 0 on success, -1 on failure
int ptrace_attach(pid_t pid);

// Detach from process
// Returns 0 on success, -1 on failure
int ptrace_detach(pid_t pid);

// Allocate memory in remote process
void* allocate_remote_mem(pid_t pid, size_t size);

// Write to remote process memory
int write_remote_mem(pid_t pid, void* remote_addr, const void* data, size_t size);

// Read from remote process memory
int read_remote_mem(pid_t pid, void* remote_addr, void* buffer, size_t size);

// Scan memory for pattern
void* mem_scan(pid_t pid, const uint8_t* pattern, const char* mask, 
               uintptr_t start, size_t length);

// Get module base address from /proc/pid/maps
uintptr_t get_module_base(pid_t pid, const char* module_name);

#endif // MORVO_INJECTOR_H
