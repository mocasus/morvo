// Morvo — Script Repository client
// Fetches scripts from backend API
package com.morvo.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Script(
    val name: String,
    val gameId: String,
    val url: String,
    val minTier: String,
    val downloads: Int
)

class ScriptRepo {
    
    private val API_BASE = "https://morvo-api.example.com"
    
    // Fetch scripts by game
    suspend fun getScripts(gameId: String, tier: String = "free"): List<Script> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE/api/v1/scripts?game_id=$gameId&tier=$tier")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val scripts = json.getJSONArray("scripts")
                
                (0 until scripts.length()).map { i ->
                    val s = scripts.getJSONObject(i)
                    Script(
                        name = s.getString("name"),
                        gameId = s.getString("game_id"),
                        url = s.getString("url"),
                        minTier = s.getString("min_tier"),
                        downloads = s.getInt("downloads")
                    )
                }
            } catch (e: Exception) {
                // Return built-in scripts on network failure
                getBuiltInScripts(gameId)
            }
        }
    }
    
    // Fetch script content from URL
    suspend fun loadScript(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Built-in scripts (shipped with APK)
    private fun getBuiltInScripts(gameId: String): List<Script> {
        return listOf(
            Script("Universal ESP", "universal", "builtin://esp", "free", 0),
            Script("Silent Aim", "universal", "builtin://aimbot", "free", 0),
            Script("Auto Farm", "universal", "builtin://autofarm", "free", 0),
            Script("Blox Fruits", "bloxfruits", "builtin://bloxfruits", "free", 0),
        )
    }
}