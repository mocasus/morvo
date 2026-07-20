// Morvo — Main Activity
// Brutalist dark theme — mocasus.my.id style
// Sharp edges, monospace, green-on-black

package com.morvo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.morvo.databinding.ActivityMainBinding
import com.morvo.injector.InjectorBridge
import com.morvo.ui.FloatingMenuService
import com.morvo.network.LicenseAPI

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var injector: InjectorBridge
    private var isAttached = false
    private var licenseTier = "free"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        injector = InjectorBridge()
        loadNativeLibrary()
        setupUI()
        validateLicense()
    }
    
    private fun loadNativeLibrary() {
        try {
            System.loadLibrary("morvo")
            binding.tvStatus.text = "> engine loaded"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, R.color.morvo_green)
            )
        } catch (e: UnsatisfiedLinkError) {
            binding.tvStatus.text = "! engine failed: ${e.message}"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, R.color.morvo_red)
            )
        }
    }
    
    private fun setupUI() {
        // Inject button
        binding.btnInject.setOnClickListener {
            if (!isAttached) {
                binding.tvStatus.text = "> scanning for roblox..."
                val success = injector.attach()
                
                if (success) {
                    isAttached = true
                    binding.btnInject.text = "// INJECTED"
                    binding.btnInject.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.morvo_green)
                    )
                    binding.tvStatus.text = "> attached to roblox"
                    showFloatingMenu()
                } else {
                    binding.tvStatus.text = "! roblox not running"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.morvo_red)
                    )
                }
            }
        }
        
        // Execute button
        binding.btnExecute.setOnClickListener {
            val script = binding.etScript.text.toString()
            if (script.isNotEmpty() && isAttached) {
                binding.tvOutput.text = "> executing..."
                val result = injector.execute(script)
                binding.tvOutput.text = "> $result"
            } else if (!isAttached) {
                binding.tvOutput.text = "! inject first"
            }
        }
        
        // Script Hub button
        binding.btnHub.setOnClickListener {
            startActivity(Intent(this, ScriptHubActivity::class.java))
        }
        
        // Status indicator
        binding.tvStatus.text = "> morvo v0.1.0 | ready"
    }
    
    private fun validateLicense() {
        val api = LicenseAPI(this)
        api.validate { valid, tier ->
            licenseTier = tier
            binding.tvTier.text = "// ${tier.uppercase()}"
        }
    }
    
    private fun showFloatingMenu() {
        val intent = Intent(this, FloatingMenuService::class.java)
        startService(intent)
    }
}