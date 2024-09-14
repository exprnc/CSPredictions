package com.exprnc.cspredictions.model

data class Tournament(
    val id: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val type: TournamentType? = null,
    val prizePool: String? = null,
    val location: String? = null,
    val mapPoolIds: List<Long>? = null,
    val formats: List<Format>? = null,
    val attendedTeamsIds: List<Long>? = null,
    val isFinished: Boolean? = null,
)
