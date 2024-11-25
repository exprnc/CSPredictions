package model

data class EGamersMatch(
    val firstTeamName: String,
    val secondTeamName: String,
    val firstTeamScore: Int,
    val secondTeamScore: Int,
    val hasFirstTeamWon: Boolean,
    val tournamentName: String,
    val bestOf: Int,
    val firstTeamCoef: Double,
    val secondTeamCoef: Double,
)
