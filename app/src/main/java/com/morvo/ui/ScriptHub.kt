// Morvo — Script Hub Activity
// Browse and execute scripts from built-in + server
package com.morvo.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.morvo.R
import com.morvo.injector.InjectorBridge
import kotlin.concurrent.thread

class ScriptHubActivity : AppCompatActivity() {
    
    private lateinit var injector: InjectorBridge
    private val scripts = mutableListOf<ScriptEntry>()
    private lateinit var adapter: ArrayAdapter<String>
    
    data class ScriptEntry(val name: String, val gameId: String, val url: String)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_hub)
        injector = InjectorBridge()
        
        val listView = findViewById<ListView>(R.id.lvScripts)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter
        
        listView.setOnItemClickListener { _, _, pos, _ ->
            val script = scripts[pos]
            loadAndExecute(script)
        }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadScripts() }
        
        loadScripts()
    }
    
    private fun loadScripts() {
        findViewById<TextView>(R.id.tvHubStatus).text = "> loading..."
        
        scripts.clear()
        scripts.add(ScriptEntry("ESP", "universal", "scripts/esp.lua"))
        scripts.add(ScriptEntry("Silent Aim", "universal", "scripts/aimbot.lua"))
        scripts.add(ScriptEntry("Auto Farm", "universal", "scripts/autofarm.lua"))
        scripts.add(ScriptEntry("Blox Fruits", "bloxfruits", "scripts/bloxfruits.lua"))
        scripts.add(ScriptEntry("Blade Ball", "bladball", "scripts/bladball.lua"))
        scripts.add(ScriptEntry("Arsenal", "arsenal", "scripts/arsenal.lua"))
        
        adapter.clear()
        adapter.addAll(scripts.map { "[${it.gameId}] ${it.name}" })
        adapter.notifyDataSetChanged()
        
        findViewById<TextView>(R.id.tvHubStatus).text = "> ${scripts.size} scripts"
    }
    
    private fun loadAndExecute(script: ScriptEntry) {
        findViewById<TextView>(R.id.tvHubStatus).text = "> executing ${script.name}..."
        
        thread {
            try {
                val content = applicationContext.assets.open(script.url)
                    .bufferedReader().use { it.readText() }
                val result = injector.execute(content)
                
                runOnUiThread {
                    findViewById<TextView>(R.id.tvHubStatus).text = "> $result"
                    Toast.makeText(this, "${script.name} executed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<TextView>(R.id.tvHubStatus).text = "! ${e.message}"
                }
            }
        }
    }
}