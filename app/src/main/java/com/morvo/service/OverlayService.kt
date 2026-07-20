// Morvo — OverlayService: Foreground service for floating executor overlay
package com.morvo.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.morvo.R
import kotlinx.coroutines.*

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: ViewGroup? = null
    private var isExpanded = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private var tvLogo: TextView? = null
    private var tvStatus: TextView? = null
    private var etScript: EditText? = null
    private var tvOutput: TextView? = null
    private var btnInject: Button? = null
    private var btnExecute: Button? = null
    private var btnHub: Button? = null
    private var btnMinimize: TextView? = null
    private var fabCollapsed: View? = null
    private var panelExpanded: ViewGroup? = null
    
    private var isInjected = false

    companion object {
        const val CHANNEL_ID = "morvo_overlay"
        const val NOTIF_ID = 996
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatingWindow()
        return START_STICKY
    }

    private fun showFloatingWindow() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_overlay, null) as ViewGroup

        // Bind views
        tvLogo = floatingView?.findViewById(R.id.tvOverlayLogo)
        tvStatus = floatingView?.findViewById(R.id.tvOverlayStatus)
        etScript = floatingView?.findViewById(R.id.etOverlayScript)
        tvOutput = floatingView?.findViewById(R.id.tvOverlayOutput)
        btnInject = floatingView?.findViewById(R.id.btnOverlayInject)
        btnExecute = floatingView?.findViewById(R.id.btnOverlayExecute)
        btnHub = floatingView?.findViewById(R.id.btnOverlayHub)
        btnMinimize = floatingView?.findViewById(R.id.btnOverlayMinimize)
        fabCollapsed = floatingView?.findViewById(R.id.fabCollapsed)
        panelExpanded = floatingView?.findViewById(R.id.panelExpanded)

        // Collapsed FAB click → expand
        fabCollapsed?.setOnClickListener {
            expand()
        }

        // Minimize click → collapse
        btnMinimize?.setOnClickListener {
            collapse()
        }

        // Inject button
        btnInject?.setOnClickListener {
            if (!isInjected) inject() else detach()
        }

        // Execute
        btnExecute?.setOnClickListener {
            val script = etScript?.text?.toString()?.trim() ?: ""
            if (script.isEmpty()) {
                toast("Enter script first")
                return@setOnClickListener
            }
            if (!isInjected) {
                toast("Inject first")
                return@setOnClickListener
            }
            execute(script)
        }

        // Script Hub
        btnHub?.setOnClickListener {
            val intent = Intent(this, com.morvo.ScriptHubActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        // Add to window
        val params = WindowManager.LayoutParams(
            if (isExpanded) 320 else 56,
            if (isExpanded) 500 else 56,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 200

        // Draggable
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, params)
    }

    private fun expand() {
        isExpanded = true
        fabCollapsed?.visibility = View.GONE
        panelExpanded?.visibility = View.VISIBLE
        
        // Resize window
        floatingView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = 320.dp()
            params.height = 480.dp()
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun collapse() {
        isExpanded = false
        fabCollapsed?.visibility = View.VISIBLE
        panelExpanded?.visibility = View.GONE
        
        floatingView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = 56.dp()
            params.height = 56.dp()
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun inject() {
        updateStatus("> scanning...", 0xFF6E7681.toInt())
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bridge = com.morvo.injector.InjectorBridge()
                    val ok = bridge.attach()
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            isInjected = true
                            updateStatus("> ACTIVE", 0xFF2EA44F.toInt())
                            tvOutput?.text = "> Injected"
                            btnInject?.text = "DETACH"
                            btnInject?.setBackgroundColor(0xFFDA3633.toInt())
                            toast("Injected!")
                        } else {
                            updateStatus("> FAILED", 0xFFDA3633.toInt())
                            tvOutput?.text = "> Roblox not found"
                            toast("Roblox not running")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateStatus("> ERROR", 0xFFDA3633.toInt())
                        tvOutput?.text = "> ${e.message}"
                    }
                }
            }
        }
    }

    private fun execute(script: String) {
        tvOutput?.text = "> executing..."
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bridge = com.morvo.injector.InjectorBridge()
                    val result = bridge.execute(script)
                    withContext(Dispatchers.Main) {
                        tvOutput?.text = "> $result"
                        toast("Done")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvOutput?.text = "> ${e.message}"
                    }
                }
            }
        }
    }

    private fun detach() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    com.morvo.injector.InjectorBridge().detach()
                    withContext(Dispatchers.Main) {
                        isInjected = false
                        updateStatus("> ready", 0xFF6E7681.toInt())
                        tvOutput?.text = "> Detached"
                        btnInject?.text = "INJECT"
                        btnInject?.setBackgroundColor(0xFF161B22.toInt())
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvOutput?.text = "> ${e.message}"
                    }
                }
            }
        }
    }

    private fun updateStatus(text: String, color: Int) {
        tvStatus?.text = text
        tvStatus?.setTextColor(color)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        floatingView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Morvo Executor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Morvo floating executor is running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Morvo")
            .setContentText("Executor is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
