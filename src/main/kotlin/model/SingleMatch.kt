package com.exprnc.cspredictions.model

data class SingleMatch(
    val id: Long? = null,
    val map: Map? = null,
    val firstTeam: Team? = null,
    val secondTeam: Team? = null,
    val firstTeamScore: Long? = null,
    val secondTeamScore: Long? = null,
    val hasFirstTeamWon: Boolean? = null,
    val isFirstTeamPick: Boolean? = null,
    val firstTeamPlayers: List<Player>? = null,
    val secondTeamPlayers: List<Player>? = null,
    val firstTeamStartedForCT: Boolean? = null,
    val firstHalfScore: Score? = null,
    val secondHalfScore: Score? = null,
    val extraTimeScore: Score? = null,
    val isFinished: Boolean? = null,
)