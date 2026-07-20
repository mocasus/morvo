// Morvo — Process Manager
// Monitors Roblox process lifecycle, auto-attach on launch
package com.morvo.injector

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class ProcessManager(private val context: Context) {
    
    private var monitoring = false
    private var onAttachCallback: (() -> Unit)? = null
    private var onDetachCallback: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastPid = 0
    
    companion object {
        const val ROBLOX_PACKAGE = "com.roblox.client"
        const val CHECK_INTERVAL = 2000L
    }
    
    fun startMonitoring(onAttach: () -> Unit, onDetach: () -> Unit) {
        monitoring = true
        onAttachCallback = onAttach
        onDetachCallback = onDetach
        checkLoop()
    }
    
    fun stopMonitoring() {
        monitoring = false
        handler.removeCallbacksAndMessages(null)
    }
    
    private fun checkLoop() {
        if (!monitoring) return
        
        val pid = getRobloxPid()
        
        if (pid > 0 && pid != lastPid) {
            lastPid = pid
            onAttachCallback?.invoke()
        } else if (pid <= 0 && lastPid > 0) {
            lastPid = 0
            onDetachCallback?.invoke()
        }
        
        handler.postDelayed({ checkLoop() }, CHECK_INTERVAL)
    }
    
    fun isRobloxRunning(): Boolean = getRobloxPid() > 0
    
    fun getRobloxPid(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return 0
        for (p in processes) {
            if (p.processName == ROBLOX_PACKAGE) return p.pid
        }
        return 0
    }
}