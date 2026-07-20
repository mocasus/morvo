// Morvo — License API client
// Multi-device key validation against backend
package com.morvo.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LicenseAPI(private val context: Context) {
    
    // TODO: Update to your VPS endpoint
    private val API_BASE = "https://morvo-api.example.com"
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("morvo_license", Context.MODE_PRIVATE)
    
    // Device fingerprint
    private fun getDeviceFingerprint(): String {
        return buildString {
            append(Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
            append("|")
            append(Build.MODEL)
            append("|")
            append(Build.MANUFACTURER)
            append("|")
            append(Build.FINGERPRINT)
        }
    }
    
    // Get stored key
    fun getStoredKey(): String? {
        return prefs.getString("key", null)
    }
    
    // Store key
    fun storeKey(key: String) {
        prefs.edit().putString("key", key).apply()
    }
    
    // Validate key against backend
    fun validate(callback: (valid: Boolean, tier: String) -> Unit) {
        val key = getStoredKey() ?: run {
            callback(false, "none")
            return
        }
        
        thread {
            try {
                val url = URL("$API_BASE/api/v1/validate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val body = JSONObject().apply {
                    put("key", key)
                    put("device_id", Build.MODEL)
                    put("hwid", getDeviceFingerprint())
                }
                
                conn.outputStream.write(body.toString().toByteArray())
                
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                val valid = json.getBoolean("valid")
                val tier = json.optString("tier", "free")
                
                callback(valid, tier)
            } catch (e: Exception) {
                // Offline fallback — accept if previously validated
                val lastTier = prefs.getString("last_tier", "none")
                val wasValid = prefs.getBoolean("was_valid", false)
                callback(wasValid, lastTier ?: "none")
            }
        }
    }
    
    // Activate new key
    fun activate(key: String, callback: (Boolean, String) -> Unit) {
        storeKey(key)
        validate(callback)
    }
    
    // Save validation result for offline use
    fun saveResult(valid: Boolean, tier: String) {
        prefs.edit()
            .putBoolean("was_valid", valid)
            .putString("last_tier", tier)
            .putLong("last_check", System.currentTimeMillis())
            .apply()
    }
}