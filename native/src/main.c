// morvo/native/src/main.c
// Morvo — Core Native Entry Point
// JNI bridge between Kotlin UI and native exploit engine

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include "injector.h"
#include "luau.h"
#include "hook.h"

#define LOG_TAG "Morvo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static JavaVM* g_jvm = NULL;
static pid_t g_roblox_pid = 0;
static void* g_lua_state = NULL;
static int g_hook_installed = 0;
static hook_t* g_loadbuffer_hook = NULL;

// ============================================================
// JNI_OnLoad — Called when native library is loaded
// ============================================================
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("[Morvo] Native library loaded v0.1.0");
    LOGI("[Morvo] ARM64 executor engine initialized");
    return JNI_VERSION_1_6;
}

// ============================================================
// nativeAttach — Attach to Roblox process
// ============================================================
JNIEXPORT jboolean JNICALL
Java_com_morvo_injector_InjectorBridge_nativeAttach(
    JNIEnv* env, jobject thiz) {
    
    LOGI("[Morvo] Scanning for Roblox process...");
    g_roblox_pid = find_roblox_pid();
    
    if (g_roblox_pid <= 0) {
        LOGE("[Morvo] Roblox not running");
        return JNI_FALSE;
    }
    
    LOGI("[Morvo] Found Roblox PID: %d", g_roblox_pid);
    
    // Phase 1: ptrace attach
    if (ptrace_attach(g_roblox_pid) != 0) {
        LOGE("[Morvo] ptrace attach failed — may need root or Hyperion bypass");
        return JNI_FALSE;
    }
    
    LOGI("[Morvo] ptrace attached successfully");
    
    // Phase 2: Find Luau VM
    LOGI("[Morvo] Scanning for lua_State...");
    g_lua_state = find_lua_state(g_roblox_pid);
    
    if (!g_lua_state) {
        LOGE("[Morvo] lua_State not found — offsets may need updating");
        ptrace_detach(g_roblox_pid);
        return JNI_FALSE;
    }
    
    LOGI("[Morvo] lua_State found at: %p", g_lua_state);
    
    // Phase 3: Install execution hooks
    LOGI("[Morvo] Installing hooks...");
    // Hook will be installed on first script execution
    
    g_hook_installed = 1;
    LOGI("[Morvo] ✅ Attached to Roblox successfully");
    return JNI_TRUE;
}

// ============================================================
// nativeExecute — Execute Lua code in Roblox
// ============================================================
JNIEXPORT jstring JNICALL
Java_com_morvo_injector_InjectorBridge_nativeExecute(
    JNIEnv* env, jobject thiz, jstring script) {
    
    if (!g_hook_installed || g_roblox_pid <= 0 || !g_lua_state) {
        return (*env)->NewStringUTF(env, "ERROR: Not attached. Inject first.");
    }
    
    const char* lua_code = (*env)->GetStringUTFChars(env, script, NULL);
    
    LOGI("[Morvo] Executing script (%zu bytes)", strlen(lua_code));
    
    char* result = lua_execute(g_roblox_pid, g_lua_state, lua_code);
    
    jstring java_result = (*env)->NewStringUTF(env, 
        result ? result : "OK — script executed");
    
    if (result) free(result);
    (*env)->ReleaseStringUTFChars(env, script, lua_code);
    
    return java_result;
}

// ============================================================
// nativeDetach — Detach from Roblox process
// ============================================================
JNIEXPORT void JNICALL
Java_com_morvo_injector_InjectorBridge_nativeDetach(
    JNIEnv* env, jobject thiz) {
    
    if (g_loadbuffer_hook) {
        unhook(g_loadbuffer_hook);
        g_loadbuffer_hook = NULL;
    }
    
    if (g_roblox_pid > 0) {
        ptrace_detach(g_roblox_pid);
        LOGI("[Morvo] Detached from Roblox");
    }
    
    g_hook_installed = 0;
    g_roblox_pid = 0;
    g_lua_state = NULL;
}

// ============================================================
// nativeGetStatus — Return executor status
// ============================================================
JNIEXPORT jstring JNICALL
Java_com_morvo_injector_InjectorBridge_nativeGetStatus(
    JNIEnv* env, jobject thiz) {
    
    char status[512];
    snprintf(status, sizeof(status),
        "Morvo v0.1.0\n"
        "Attached: %s\n"
        "Roblox PID: %d\n"
        "lua_State: %p\n"
        "Hook active: %s",
        g_hook_installed ? "YES" : "NO",
        g_roblox_pid,
        g_lua_state,
        g_loadbuffer_hook ? "YES" : "NO"
    );
    
    return (*env)->NewStringUTF(env, status);
}
