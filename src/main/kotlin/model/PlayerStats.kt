package com.exprnc.cspredictions.model

data class PlayerStats(
    val kills: Long? = null,
    val deaths: Long? = null,
    val firstKills: Long? = null,
    val firstDeaths: Long? = null,
    val headshotKills: Long? = null,
    val assists: Long? = null,
    val flashAssists: Long? = null,
    val KAST: Float? = null, // Percentage of rounds in which the player either had a kill, assist, survived or was traded
    val ADR: Float? = null, // average damage per round
    val rating: Float? = null, // rating 2.0
)
