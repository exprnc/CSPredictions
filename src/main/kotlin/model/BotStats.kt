package model

data class BotStats(
    val totalMatches: Int,
    val wins: Int,
    val fails: Int,
    val winRate: Double,
    val predicted: Double
)