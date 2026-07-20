// Morvo — Device fingerprint for anti-ban
// Generates unique device ID without exposing real identifiers
package com.morvo.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object DeviceFingerprint {
    
    private const val PREFS_NAME = "morvo_device"
    private const val KEY_FP = "device_fp"
    
    // Generate or retrieve device fingerprint
    fun getFingerprint(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Return cached fingerprint
        prefs.getString(KEY_FP, null)?.let { return it }
        
        // Generate new fingerprint
        val fp = generateFingerprint(context)
        prefs.edit().putString(KEY_FP, fp).apply()
        
        return fp
    }
    
    // Generate spoofed fingerprint
    fun getSpoofedFingerprint(context: Context): String {
        return "morvo-" + UUID.randomUUID().toString().take(8)
    }
    
    // Rotate fingerprint (anti-ban)
    fun rotateFingerprint(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newFp = generateFingerprint(context)
        prefs.edit().putString(KEY_FP, newFp).apply()
        return newFp
    }
    
    private fun generateFingerprint(context: Context): String {
        val raw = buildString {
            append(Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
            append(Build.MODEL)
            append(Build.MANUFACTURER)
            append(Build.HARDWARE)
        }
        
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}