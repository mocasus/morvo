# Minimal CMakeLists — Native code builds separately via tools/
# This is a placeholder for the Android Studio build system

cmake_minimum_required(VERSION 3.22.1)
project("morvo")

# Add native library when sources are ready
# add_library(morvo SHARED
#     ${CMAKE_SOURCE_DIR}/../../../native/src/main.c
# )
# target_include_directories(morvo PRIVATE ${CMAKE_SOURCE_DIR}/../../../native/include)