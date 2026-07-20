// Morvo — Main Activity (minimal buildable)
package com.morvo

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val tv = TextView(this).apply {
            text = """
╔══════════════════╗
║     M O R V O    ║
║   v0.5.0 BETA    ║
║  Roblox Executor ║
╚══════════════════╝

> engine: morvo.so
> status: idle
> connect device to begin

github.com/mocasus/morvo
            """.trimIndent()
            textSize = 11f
            setTextColor(0xFF2EA44F.toInt())
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(32, 32, 32, 32)
        }
        setContentView(tv)
    }
}