package com.exprnc.cspredictions.model

data class RoundHistory(
    val score: Score? = null,
    val hasFirstTeamWon: Boolean? = null,
    val reason: Reason? = null
)
