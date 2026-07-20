// Morvo — Main Activity v0.6.2
// Roblox Script Executor with native engine
package com.morvo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    private lateinit var tvTier: TextView
    private lateinit var tvOutput: TextView
    private lateinit var etScript: EditText
    private lateinit var btnInject: Button
    private lateinit var btnExecute: Button
    private lateinit var btnHub: Button
    
    private var isInjected = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvStatus = findViewById(R.id.tvStatus)
        tvTier = findViewById(R.id.tvTier)
        tvOutput = findViewById(R.id.tvOutput)
        etScript = findViewById(R.id.etScript)
        btnInject = findViewById(R.id.btnInject)
        btnExecute = findViewById(R.id.btnExecute)
        btnHub = findViewById(R.id.btnHub)
        
        // Check license
        scope.launch {
            checkLicense()
        }
        
        // Inject button
        btnInject.setOnClickListener {
            if (!isInjected) {
                doInject()
            } else {
                doDetach()
            }
        }
        
        // Execute button
        btnExecute.setOnClickListener {
            val script = etScript.text.toString().trim()
            if (script.isEmpty()) {
                toast("Enter a script first")
                return@setOnClickListener
            }
            if (!isInjected) {
                toast("Inject first before executing")
                return@setOnClickListener
            }
            doExecute(script)
        }
        
        // Script Hub
        btnHub.setOnClickListener {
            toast("Script Hub — coming soon")
        }
    }
    
    private suspend fun checkLicense() {
        val app = application as MorvoApp
        withContext(Dispatchers.IO) {
            app.licenseAPI.validate { valid, tier, message ->
                runOnUiThread {
                    tvTier.text = "// ${tier.uppercase()}"
                    tvTier.setTextColor(if (valid) 0xFF2EA44F.toInt() else 0xFFDA3633.toInt())
                    if (!valid) {
                        tvStatus.text = "> key: invalid — tap to activate"
                        tvStatus.setTextColor(0xFFDA3633.toInt())
                        tvStatus.setOnClickListener { showKeyDialog() }
                    } else {
                        tvStatus.text = "> morvo v0.6.2 | ${tier.lowercase()} | ready"
                        tvStatus.setTextColor(0xFF2EA44F.toInt())
                        tvStatus.setOnClickListener(null)
                    }
                }
            }
        }
    }
    
    private fun showKeyDialog() {
        val input = EditText(this)
        input.hint = "morvo-free-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        input.setTextColor(0xFFE6EDF3.toInt())
        input.setHintTextColor(0xFF30363D.toInt())
        input.setBackgroundColor(0xFF111111.toInt())
        input.setPadding(32, 32, 32, 32)
        input.textSize = 12f
        
        AlertDialog.Builder(this, R.style.MorvoDialog)
            .setTitle("Activate Key")
            .setView(input)
            .setPositiveButton("ACTIVATE") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    activateKey(key)
                }
            }
            .setNegativeButton("GET KEY") { _, _ ->
                val app = application as MorvoApp
                app.licenseAPI.openGateway()
            }
            .setNeutralButton("CANCEL", null)
            .show()
    }
    
    private fun activateKey(key: String) {
        tvStatus.text = "> validating..."
        val app = application as MorvoApp
        app.licenseAPI.activate(key) { valid, tier, message ->
            runOnUiThread {
                if (valid) {
                    tvTier.text = "// ${tier.uppercase()}"
                    tvTier.setTextColor(0xFF2EA44F.toInt())
                    tvStatus.text = "> morvo v0.6.2 | ${tier.lowercase()} | ready"
                    tvStatus.setTextColor(0xFF2EA44F.toInt())
                    tvStatus.setOnClickListener(null)
                    tvOutput.text = "> ✅ Key activated — $tier tier"
                    toast("Key activated!")
                } else {
                    tvStatus.text = "> key: invalid — tap to activate"
                    tvStatus.setTextColor(0xFFDA3633.toInt())
                    tvOutput.text = "> ❌ $message"
                    toast("Invalid key: $message")
                }
            }
        }
    }
    
    private fun doInject() {
        tvStatus.text = "> scanning for Roblox..."
        tvOutput.text = "> attaching..."
        
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bridge = com.morvo.injector.InjectorBridge()
                    val ok = bridge.attach()
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            isInjected = true
                            tvStatus.text = "> attached | status: active"
                            tvStatus.setTextColor(0xFF2EA44F.toInt())
                            tvOutput.text = "> ✅ Injected successfully\n> Ready to execute scripts"
                            btnInject.text = "DETACH"
                            btnInject.backgroundTintList = 
                                android.content.res.ColorStateList.valueOf(0xFFDA3633.toInt())
                            toast("Injected successfully")
                        } else {
                            tvStatus.text = "> attach failed"
                            tvStatus.setTextColor(0xFFDA3633.toInt())
                            tvOutput.text = "> ❌ Injection failed\n> Make sure Roblox is running first"
                            toast("Injection failed — Roblox not found")
                        }
                    }
                } catch (e: UnsatisfiedLinkError) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "> native lib not loaded"
                        tvStatus.setTextColor(0xFFDA3633.toInt())
                        tvOutput.text = "> ❌ Native engine not available\n> This device may not be supported"
                        toast("Native library not loaded")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "> error: ${e.message}"
                        tvOutput.text = "> ❌ ${e.message}"
                    }
                }
            }
        }
    }
    
    private fun doExecute(script: String) {
        tvOutput.text = "> executing..."
        
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bridge = com.morvo.injector.InjectorBridge()
                    val result = bridge.execute(script)
                    withContext(Dispatchers.Main) {
                        tvOutput.text = "> $result"
                        tvStatus.text = "> attached | script executed"
                        toast("Script executed")
                    }
                } catch (e: UnsatisfiedLinkError) {
                    withContext(Dispatchers.Main) {
                        tvOutput.text = "> ❌ Native engine unavailable"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvOutput.text = "> ❌ ${e.message}"
                    }
                }
            }
        }
    }
    
    private fun doDetach() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bridge = com.morvo.injector.InjectorBridge()
                    bridge.detach()
                    withContext(Dispatchers.Main) {
                        isInjected = false
                        tvStatus.text = "> detached | ready"
                        tvStatus.setTextColor(0xFF6E7681.toInt())
                        tvOutput.text = "> Detached from Roblox"
                        btnInject.text = "INJECT"
                        btnInject.backgroundTintList = 
                            android.content.res.ColorStateList.valueOf(0xFF161B22.toInt())
                        toast("Detached")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvOutput.text = "> ❌ ${e.message}"
                    }
                }
            }
        }
    }
    
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
