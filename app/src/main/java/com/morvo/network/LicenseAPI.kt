// Morvo — License API client v0.6.0
// Key validation + Loot-Link gateway redirect
package com.morvo.network

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LicenseAPI(private val context: Context) {

    // Production endpoint — via nginx reverse proxy
    private val API_BASE = "https://43.134.131.85/morvo"
    private val GATEWAY_URL = "$API_BASE/api/v1/gateway/redirect"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("morvo_license", Context.MODE_PRIVATE)

    // Device fingerprint — unique per device
    private fun getDeviceFingerprint(): String {
        return buildString {
            append(Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
            append("|"); append(Build.MODEL)
            append("|"); append(Build.MANUFACTURER)
            append("|"); append(Build.FINGERPRINT)
        }
    }

    // Get client IP (approximate — for rate limiting purposes)
    private fun getClientIP(): String {
        return try {
            // Simple approach: use external service
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun getStoredKey(): String? = prefs.getString("key", null)

    fun storeKey(key: String) {
        prefs.edit().putString("key", key).apply()
    }

    fun getStoredTier(): String = prefs.getString("last_tier", "none") ?: "none"

    fun isKeyValid(): Boolean = prefs.getBoolean("was_valid", false)

    // Open Loot-Link gateway for key generation
    fun openGateway(tier: String = "free") {
        val uri = Uri.parse("$GATEWAY_URL?tier=$tier")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // Validate key against Morvo backend
    fun validate(callback: (valid: Boolean, tier: String, message: String) -> Unit) {
        val key = getStoredKey() ?: run {
            callback(false, "none", "No key stored — tap 'Get Key' to get one")
            return
        }

        thread {
            try {
                val url = URL("$API_BASE/api/v1/validate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val body = JSONObject().apply {
                    put("key", key)
                    put("device_id", Build.MODEL)
                    put("hwid", getDeviceFingerprint())
                    put("client_ip", getClientIP())
                }

                conn.outputStream.write(body.toString().toByteArray())
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val valid = json.getBoolean("valid")
                val tier = json.optString("tier", "free")
                val message = json.optString("message", "")

                // Cache validation result for offline use
                if (valid) {
                    saveResult(true, tier, json.optLong("expires_at", 0))
                } else {
                    saveResult(false, "none", 0)
                }

                callback(valid, tier, message)
            } catch (e: Exception) {
                // Offline fallback — accept if previously validated and within 24h
                val lastTier = prefs.getString("last_tier", "none") ?: "none"
                val wasValid = prefs.getBoolean("was_valid", false)
                val lastCheck = prefs.getLong("last_check", 0)
                val hoursSinceCheck = (System.currentTimeMillis() - lastCheck) / 3600000

                if (wasValid && hoursSinceCheck < 24) {
                    callback(true, lastTier, "Offline mode — using cached key")
                } else {
                    callback(false, "none", "Network error — check your connection")
                }
            }
        }
    }

    // Activate new key (store + validate)
    fun activate(key: String, callback: (Boolean, String, String) -> Unit) {
        storeKey(key)
        validate(callback)
    }

    // Clear stored key
    fun clearKey() {
        prefs.edit().remove("key").remove("was_valid").remove("last_tier").apply()
    }

    // Save validation result for offline cache
    private fun saveResult(valid: Boolean, tier: String, expiresAt: Long) {
        prefs.edit()
            .putBoolean("was_valid", valid)
            .putString("last_tier", tier)
            .putLong("last_check", System.currentTimeMillis())
            .putLong("expires_at", expiresAt)
            .apply()
    }

    // Check if key is about to expire (warn user within 2 hours)
    fun isAboutToExpire(): Boolean {
        val expiresAt = prefs.getLong("expires_at", 0)
        if (expiresAt == 0L) return false
        val hoursLeft = (expiresAt * 1000 - System.currentTimeMillis()) / 3600000
        return hoursLeft in 1..2
    }

    fun getExpiryHours(): Long {
        val expiresAt = prefs.getLong("expires_at", 0)
        if (expiresAt == 0L) return 0
        return (expiresAt * 1000 - System.currentTimeMillis()) / 3600000
    }
}
