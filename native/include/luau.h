// morvo/native/include/luau.h
#ifndef MORVO_LUAU_H
#define MORVO_LUAU_H

#include <stdint.h>
#include <sys/types.h>

// Luau VM state pointer
typedef void* lua_State;

// Find lua_State in Roblox process memory
// Uses signature scanning to locate the root Lua VM
void* find_lua_state(pid_t pid);

// Execute Lua code in Roblox's Lua VM
// Returns heap-allocated result string (caller must free)
char* lua_execute(pid_t pid, void* L, const char* script);

// Set script identity level
// 0 = Plugin, 1 = LocalScript, 2 = Script, 6 = CoreScript, 8 = RobloxScript
// Returns old identity level
int lua_set_identity(void* L, int level);

// Get Luau VM globals environment
void* lua_get_globals(void* L);

// Look up Luau function by name
void* lua_get_function(void* L, const char* module, const char* name);

#endif // MORVO_LUAU_H
