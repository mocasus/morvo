// Morvo — Game Hub RecyclerView adapter
package com.morvo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.morvo.R
import com.morvo.model.RobloxGame
import com.morvo.service.OverlayService
import java.text.NumberFormat
import java.util.Locale

class GameHubAdapter(
    private val ctx: Context,
    private val games: List<RobloxGame>
) : RecyclerView.Adapter<GameHubAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val name: TextView = v.findViewById(R.id.tvGameName)
        val creator: TextView = v.findViewById(R.id.tvCreator)
        val stats: TextView = v.findViewById(R.id.tvStats)
        val btnPlay: TextView = v.findViewById(R.id.btnPlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_game, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val game = games[pos]
        h.name.text = game.name
        h.creator.text = "by ${game.creatorName}"
        h.stats.text = "👥 ${formatNum(game.playerCount)}  •  👁 ${formatNum(game.visits)}"
        
        h.btnPlay.setOnClickListener {
            // Start floating overlay service
            ctx.startService(Intent(ctx, OverlayService::class.java))
            
            // Launch Roblox deep link to the game
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.roblox.com/games/${game.id}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(intent)
            } catch (e: Exception) {
                // Fallback: open in browser if Roblox not installed
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.roblox.com/games/${game.id}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(fallback)
            }
        }
    }

    override fun getItemCount(): Int = games.size

    private fun formatNum(n: Long): String {
        return when {
            n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
            n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }
}
