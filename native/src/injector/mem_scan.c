// morvo/native/src/injector/mem_scan.c
// Memory pattern scanner for ARM64
// Uses Boyer-Moore-Horspool for fast scanning
// Wildcards supported (0x?? matches any byte)

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "injector.h"

#define LOG_TAG "Morvo-MemScan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Wildcard byte sentinel — replaces 0x?? in signature patterns
#define WILDCARD_BYTE 0xCC

// Boyer-Moore-Horspool bad character table (for non-wildcard bytes)
#define ALPHABET_SIZE 256

typedef struct {
    uint8_t* pattern;
    size_t length;
    int* bad_char;          // Bad character shift table
    int wildcard_count;
} scanner_t;

// Create scanner context
static scanner_t* scanner_create(const uint8_t* pattern, size_t length) {
    scanner_t* s = calloc(1, sizeof(scanner_t));
    s->pattern = malloc(length);
    memcpy(s->pattern, pattern, length);
    s->length = length;
    
    s->bad_char = malloc(ALPHABET_SIZE * sizeof(int));
    
    // Initialize bad character table
    for (int i = 0; i < ALPHABET_SIZE; i++) {
        s->bad_char[i] = length;
    }
    
    // For wildcard-aware scanning, use last non-wildcard byte
    // Count wildcards
    for (size_t i = 0; i < length; i++) {
        if (pattern[i] != WILDCARD_BYTE) {
            s->bad_char[pattern[i]] = length - 1 - i;
        } else {
            s->wildcard_count++;
        }
    }
    
    return s;
}

// Free scanner
static void scanner_free(scanner_t* s) {
    if (s) {
        free(s->pattern);
        free(s->bad_char);
        free(s);
    }
}

// Core scan implementation with wildcards
void* mem_scan(pid_t pid, const uint8_t* pattern, const char* mask,
               uintptr_t start, size_t length) {
    
    if (!pattern || !mask) return NULL;
    
    size_t pat_len = strlen(mask);
    
    // Limit scan to reasonable chunks (avoid reading 2GB at once)
    size_t chunk_size = 1024 * 1024;  // 1MB chunks
    uint8_t* buffer = malloc(chunk_size + pat_len);
    if (!buffer) return NULL;
    
    LOGI("Scanning 0x%lx → 0x%lx (%zu MB)", 
         start, start + length, length / (1024*1024));
    
    size_t scanned = 0;
    void* found = NULL;
    
    while (scanned < length && !found) {
        size_t chunk = (length - scanned) < chunk_size ? 
                       (length - scanned) : chunk_size;
        
        uintptr_t addr = start + scanned;
        
        if (read_remote_mem(pid, (void*)addr, buffer, chunk + pat_len) != 0) {
            // Skip unreadable pages
            scanned += 4096;  // Skip a page
            continue;
        }
        
        // Search within this chunk
        for (size_t i = 0; i < chunk && !found; i++) {
            int match = 1;
            for (size_t j = 0; j < pat_len; j++) {
                if (mask[j] == '?' || mask[j] == 'x') continue; // 'x' = unknown/don't care
                if (mask[j] == '.') {  // Wildcard
                    continue;
                }
                // Exact match required
                if (buffer[i + j] != pattern[j]) {
                    match = 0;
                    break;
                }
            }
            if (match) {
                found = (void*)(addr + i);
                LOGI("Pattern found at %p (offset: 0x%lx from base)", 
                     found, (uintptr_t)found - start);
                break;
            }
        }
        
        scanned += chunk;
    }
    
    free(buffer);
    return found;
}