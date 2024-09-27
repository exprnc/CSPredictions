package model

data class Match(
    val id: Long,
    val bestOf: Int,
    val tournament: Tournament,
    val firstTeam: Team,
    val secondTeam: Team,
    val firstTeamRanking: Int,
    val secondTeamRanking: Int,
    val firstTeamScore: Int,
    val secondTeamScore: Int,
    val hasFirstTeamWon: Boolean,
    val firstTeamPlayers: List<Player>,
    val secondTeamPlayers: List<Player>,
)