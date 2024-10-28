package model

import bot.calibrator.Glicko2

data class PlayerStats(
    val playerId: Long = 0L,
    var name: String = "",
    var rating: Int = 1400,
    var matchCount: Int = 0,
    var glicko: Rating = Glicko2.createRating()
)