cmake_minimum_required(VERSION 3.22)
project("morvo")

add_library(morvo SHARED
    src/main.c
    src/injector/ptrace_attach.c
    src/injector/mem_scan.c
    src/injector/proc_maps.c
    src/luau/luastate.c
    src/luau/execute.c
    src/luau/identity.c
    src/hook/arm64_hook.c
    src/hook/arm32_hook.c
    src/bypass/hyperion.c
    src/bypass/integrity.c
)

target_include_directories(morvo PRIVATE include)
target_link_libraries(morvo dl log)
