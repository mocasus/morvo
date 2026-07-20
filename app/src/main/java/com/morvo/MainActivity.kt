// Morvo — Game Hub (MainActivity) v0.7.0
// Shows Roblox games. Tap PLAY → floating overlay + launch game
package com.morvo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.morvo.model.RobloxGame
import com.morvo.service.OverlayService
import com.morvo.ui.GameHubAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var tvTier: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etSearch: EditText
    private lateinit var rvGames: RecyclerView
    private lateinit var btnScriptHub: Button
    private lateinit var adapter: GameHubAdapter

    companion object {
        // Popular Roblox games (hardcoded — API integration later)
        val POPULAR_GAMES = listOf(
            RobloxGame(2753915549, "Blox Fruits", "Fight, explore, collect fruits", "", 185234, 34000000000, "Gamer Robot Inc"),
            RobloxGame(4483381587, "Brookhaven 🏡", "Roleplay in a town", "", 412567, 52000000000, "Wolfpaq"),
            RobloxGame(4924922222, "Adopt Me!", "Raise pets, build homes", "", 389124, 37000000000, "DreamCraft"),
            RobloxGame(142823291, "Murder Mystery 2", "Sheriff vs Murderer", "", 127893, 15000000000, "Nikilis"),
            RobloxGame(537413528, "Build A Boat For Treasure", "Build boats, find treasure", "", 98765, 12000000000, "Chillz Studios"),
            RobloxGame(15532962292, "Doors", "Survive the hotel", "", 215432, 8000000000, "LSplash"),
            RobloxGame(6516141723, "Doors Floor 2", "The hotel continues", "", 189654, 5000000000, "LSplash"),
            RobloxGame(9872472334, "Fisch", "Go fishing, collect fish", "", 312456, 12000000000, "Fisch Dev"),
            RobloxGame(196208686, "Tower of Hell", "Climb to the top", "", 67890, 9500000000, "Yxcexn"),
            RobloxGame(6872274481, "BedWars", "Destroy beds, win", "", 56789, 7500000000, "Easy.gg"),
            RobloxGame(18505805302, "Rivals", "1v1 arena shooter", "", 134567, 3000000000, "Nosniy Games"),
            RobloxGame(920587237, "Arsenal", "Gun game FPS", "", 45678, 11000000000, "ROLVe"),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_hub)

        tvTier = findViewById(R.id.tvHubTier)
        tvStatus = findViewById(R.id.tvHubStatus)
        etSearch = findViewById(R.id.etSearch)
        rvGames = findViewById(R.id.rvGames)
        btnScriptHub = findViewById(R.id.btnHubScripts)

        // Setup RecyclerView
        adapter = GameHubAdapter(this, POPULAR_GAMES)
        rvGames.layoutManager = LinearLayoutManager(this)
        rvGames.adapter = adapter

        // Script Hub button
        btnScriptHub.setOnClickListener {
            startActivity(Intent(this, ScriptHubActivity::class.java))
        }

        // Key activation
        tvStatus.setOnClickListener {
            showKeyDialog()
        }

        // Check license
        val app = application as MorvoApp
        app.licenseAPI.validate { valid, tier, message ->
            runOnUiThread {
                tvTier.text = "// ${tier.uppercase()}"
                if (valid) {
                    tvTier.setTextColor(0xFF2EA44F.toInt())
                    tvStatus.text = "> morvo v0.7.0 | ${tier.lowercase()} | ready"
                    tvStatus.setTextColor(0xFF2EA44F.toInt())
                } else {
                    tvTier.setTextColor(0xFFDA3633.toInt())
                    tvStatus.text = "> key invalid — tap here"
                    tvStatus.setTextColor(0xFFDA3633.toInt())
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

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.MorvoDialog)
            .setTitle("Activate Key")
            .setView(input)
            .setPositiveButton("ACTIVATE") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    val app = application as MorvoApp
                    app.licenseAPI.activate(key) { valid, tier, message ->
                        runOnUiThread {
                            if (valid) {
                                tvTier.text = "// ${tier.uppercase()}"
                                tvTier.setTextColor(0xFF2EA44F.toInt())
                                tvStatus.text = "> morvo v0.7.0 | ${tier.lowercase()} | ready"
                                tvStatus.setTextColor(0xFF2EA44F.toInt())
                                Toast.makeText(this, "Key activated!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Invalid: $message", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("GET KEY") { _, _ ->
                val app = application as MorvoApp
                app.licenseAPI.openGateway()
            }
            .setNeutralButton("CANCEL", null)
            .show()
    }
}
