// Morvo — Game model for Roblox games
package com.morvo.model

data class RobloxGame(
    val id: Long,
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val playerCount: Long,
    val visits: Long,
    val creatorName: String
)
