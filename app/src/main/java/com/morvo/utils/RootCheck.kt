// Morvo — Root detection utility
package com.morvo.utils

import java.io.File

object RootCheck {
    
    // Check if device is rooted
    fun isRooted(): Boolean {
        return checkBuildTags() || checkSuBinary() || checkMagisk() || checkRootPaths()
    }
    
    // Check build tags for test-keys
    private fun checkBuildTags(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
    
    // Check for su binary
    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system/sbin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }
    
    // Check for Magisk
    private fun checkMagisk(): Boolean {
        val paths = arrayOf(
            "/sbin/magisk",
            "/data/adb/magisk",
            "/system/app/Magisk"
        )
        return paths.any { File(it).exists() }
    }
    
    // Check for common root paths
    private fun checkRootPaths(): Boolean {
        val paths = arrayOf(
            "/data/adb",
            "/system/etc/init.d",
            "/vendor/etc/init.d",
            "/system/app/Superuser"
        )
        return paths.any { File(it).exists() }
    }
    
    // Check if running in emulator
    fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK") ||
                android.os.Build.MANUFACTURER.contains("Genymotion"))
    }
}