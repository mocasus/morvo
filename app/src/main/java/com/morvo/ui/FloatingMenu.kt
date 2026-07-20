// Morvo — Floating overlay menu service
// Brutalist dark minimal overlay for in-game script execution
package com.morvo.ui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.morvo.R
import com.morvo.injector.InjectorBridge

class FloatingMenuService : Service() {
    
    private lateinit var wm: WindowManager
    private lateinit var overlay: View
    private var expanded = false
    private val injector = InjectorBridge()
    
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 16
        params.y = 200
        
        wm.addView(overlay, params)
        
        // Drag
        val dragHandle = overlay.findViewById<View>(R.id.dragHandle)!!
        var initX = 0; var initY = 0; var initTX = 0f; var initTY = 0f
        
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = (overlay.layoutParams as WindowManager.LayoutParams).x
                    initY = (overlay.layoutParams as WindowManager.LayoutParams).y
                    initTX = event.rawX; initTY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = overlay.layoutParams as WindowManager.LayoutParams
                    lp.x = initX + (initTX - event.rawX).toInt()
                    lp.y = initY + (event.rawY - initTY).toInt()
                    wm.updateViewLayout(overlay, lp)
                    true
                }
                else -> false
            }
        }
        
        // Toggle
        overlay.findViewById<Button>(R.id.btnToggle).setOnClickListener {
            expanded = !expanded
            overlay.findViewById<View>(R.id.expandedMenu).visibility = 
                if (expanded) View.VISIBLE else View.GONE
        }
        
        // Quick executes
        setupButton(R.id.btnESP, "esp")
        setupButton(R.id.btnAimbot, "aimbot")
        setupButton(R.id.btnFarm, "autofarm")
        
        // Close
        overlay.findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }
    }
    
    private fun setupButton(btnId: Int, scriptName: String) {
        overlay.findViewById<Button>(btnId).setOnClickListener {
            try {
                val script = applicationContext.assets
                    .open("scripts/universal/$scriptName.lua")
                    .bufferedReader().use { it.readText() }
                val result = injector.execute(script)
                overlay.findViewById<TextView>(R.id.tvOverlayOutput).text = "> $result"
            } catch (e: Exception) {
                overlay.findViewById<TextView>(R.id.tvOverlayOutput).text = "! ${e.message}"
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        wm.removeView(overlay)
        super.onDestroy()
    }
}