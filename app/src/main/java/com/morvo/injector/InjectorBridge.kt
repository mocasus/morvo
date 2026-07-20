// Morvo — JNI Bridge to native engine
package com.morvo.injector

class InjectorBridge {
    
    // Native methods (implemented in native/src/main.c)
    external fun nativeAttach(): Boolean
    external fun nativeExecute(script: String): String
    external fun nativeDetach()
    external fun nativeGetStatus(): String
    
    fun attach(): Boolean {
        return nativeAttach()
    }
    
    fun execute(script: String): String {
        return nativeExecute(script)
    }
    
    fun detach() {
        nativeDetach()
    }
    
    fun getStatus(): String {
        return nativeGetStatus()
    }
}