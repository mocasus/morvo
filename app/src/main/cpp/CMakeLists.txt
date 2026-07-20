cmake_minimum_required(VERSION 3.22)
project("morvo")

# Native sources from /native/
set(NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native)

add_library(morvo SHARED
    ${NATIVE_ROOT}/src/main.c
    ${NATIVE_ROOT}/src/injector/ptrace_attach.c
    ${NATIVE_ROOT}/src/injector/mem_scan.c
    ${NATIVE_ROOT}/src/injector/proc_maps.c
    ${NATIVE_ROOT}/src/luau/luastate.c
    ${NATIVE_ROOT}/src/luau/execute.c
    ${NATIVE_ROOT}/src/luau/identity.c
    ${NATIVE_ROOT}/src/hook/arm64_hook.c
    ${NATIVE_ROOT}/src/hook/arm32_hook.c
    ${NATIVE_ROOT}/src/bypass/hyperion.c
    ${NATIVE_ROOT}/src/bypass/integrity.c
)

target_include_directories(morvo PRIVATE ${NATIVE_ROOT}/include)
target_link_libraries(morvo dl log)
