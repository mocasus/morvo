// Morvo — Script Hub Activity v0.7.0
package com.morvo

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.content.Intent
import com.morvo.service.OverlayService

class ScriptHubActivity : AppCompatActivity() {

    private lateinit var rvScripts: RecyclerView
    private lateinit var tvCategory: TextView
    private val scripts = mutableListOf<ScriptItem>()
    private val allScripts = listOf(
        ScriptItem("aimbot", "Universal Aimlock", "lua", "Aimbot for all FPS games\nlocal player = game.Players.LocalPlayer..."),
        ScriptItem("esp", "ESP Wallhack", "lua", "See players through walls\nlocal function highlight..."),
        ScriptItem("fly", "Fly GUI", "lua", "Noclip fly script\nlocal player = game.Players.LocalPlayer..."),
        ScriptItem("autofarm", "Auto Farm", "lua", "Auto collect coins/xp\nwhile wait() do..."),
        ScriptItem("tp", "Teleport GUI", "lua", "Click to teleport\nlocal mouse = player:GetMouse()..."),
        ScriptItem("speed", "Speed Hack", "lua", "Increase walkspeed\nplayer.Character.Humanoid.WalkSpeed = 50"),
        ScriptItem("infjump", "Infinite Jump", "lua", "Jump infinitely\nlocal UserInputService = game:GetService..."),
        ScriptItem("bTools", "BTools / Grab Knife", "lua", "Spawn BTools\nlocal tool = Instance.new..."),
        ScriptItem("dex", "Dark Dex Explorer", "lua", "Dex v4 explorer\nloadstring(game:HttpGet..."),
        ScriptItem("kill", "Kill Aura", "lua", "Auto kill nearby players\nfor _,v in pairs..."),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_hub)

        tvCategory = findViewById(R.id.tvHubCategory)
        rvScripts = findViewById(R.id.rvScripts)
        rvScripts.layoutManager = LinearLayoutManager(this)

        scripts.addAll(allScripts)
        rvScripts.adapter = ScriptAdapter(scripts) { script ->
            // Copy to clipboard and show toast
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("script", script.code))
            Toast.makeText(this, "Script copied! Paste in overlay", Toast.LENGTH_SHORT).show()
            
            // Also try to start overlay if not running
            startService(Intent(this, OverlayService::class.java))
        }
    }

    data class ScriptItem(
        val id: String,
        val title: String,
        val lang: String,
        val code: String
    )
}

class ScriptAdapter(
    private val items: List<ScriptHubActivity.ScriptItem>,
    private val onClick: (ScriptHubActivity.ScriptItem) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvScriptTitle)
        val lang: TextView = v.findViewById(R.id.tvScriptLang)
        val preview: TextView = v.findViewById(R.id.tvScriptPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val s = items[pos]
        h.title.text = s.title
        h.lang.text = ".${s.lang}"
        h.preview.text = s.code.take(80)
        h.itemView.setOnClickListener { onClick(s) }
    }

    override fun getItemCount(): Int = items.size
}
