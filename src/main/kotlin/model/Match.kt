package com.exprnc.cspredictions.model

data class Match(
    val id: Long? = null,
    val url: String? = null,
    val tournament: Tournament? = null,
    val description: String? = null,
    val bestOf: Long? = null,
    val tier: Long? = null,
    val firstTeamId: Long? = null,
    val secondTeamId: Long? = null,
    val firstTeamRanking: Long? = null,
    val secondTeamRanking: Long? = null,
    val firstTeamScore: Long? = null,
    val secondTeamScore: Long? = null,
    val hasFirstTeamWon: Boolean? = null,
    val singleMatches: List<SingleMatch>? = null,
    val isFinished: Boolean? = null,
    val startDate: String? = null,
)