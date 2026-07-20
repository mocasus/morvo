// morvo/native/src/injector/proc_maps.c
// Parse /proc/pid/maps for memory layout analysis
// Extracts: module base addresses, permissions, region sizes

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "injector.h"

#define LOG_TAG "Morvo-ProcMaps"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Memory region descriptor
typedef struct {
    uintptr_t start;
    uintptr_t end;
    char perms[8];
    uintptr_t offset;
    char path[256];
} memory_region_t;

// Parse a single line from /proc/pid/maps
// Format: start-end perms offset dev inode pathname
static int parse_maps_line(const char* line, memory_region_t* region) {
    memset(region, 0, sizeof(*region));
    
    int n = sscanf(line, "%lx-%lx %7s %lx %*s %*d %255s",
                   &region->start, &region->end,
                   region->perms, &region->offset,
                   region->path);
    
    return (n >= 4) ? 0 : -1;
}

// Find the base address of a specific module
uintptr_t get_module_base(pid_t pid, const char* module_name) {
    char maps_path[256];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
    
    FILE* fp = fopen(maps_path, "r");
    if (!fp) {
        LOGE("Cannot open %s: %s", maps_path, strerror(errno));
        return 0;
    }
    
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        memory_region_t region;
        if (parse_maps_line(line, &region) != 0) continue;
        
        if (strstr(region.path, module_name)) {
            // Return the first executable region of the module
            if (region.perms[2] == 'x') {
                fclose(fp);
                LOGI("%s base: 0x%lx", module_name, region.start);
                return region.start;
            }
        }
    }
    
    fclose(fp);
    LOGI("%s not found in /proc/%d/maps", module_name, pid);
    return 0;
}

// List all loaded modules
int list_loaded_modules(pid_t pid) {
    char maps_path[256];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", pid);
    
    FILE* fp = fopen(maps_path, "r");
    if (!fp) return -1;
    
    LOGI("=== Loaded Modules (PID %d) ===", pid);
    
    char line[512];
    char last_path[256] = "";
    
    while (fgets(line, sizeof(line), fp)) {
        memory_region_t region;
        if (parse_maps_line(line, &region) != 0) continue;
        
        // Only print unique modules (first occurrence)
        if (region.path[0] && strcmp(region.path, last_path) != 0) {
            strcpy(last_path, region.path);
            LOGI("  0x%lx-0x%lx %s %s", 
                 region.start, region.end, region.perms, region.path);
        }
    }
    
    fclose(fp);
    return 0;
}
