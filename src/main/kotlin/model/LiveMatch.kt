package model

data class LiveMatch(
    val matchId: Long,
    val matchUrl: String,
    val bestOf: Int,
    val tournament: Tournament,
    val firstTeam: Team,
    val secondTeam: Team,
    val firstTeamRanking: Int,
    val secondTeamRanking: Int,
    val firstTeamPlayers: List<Player>,
    val secondTeamPlayers: List<Player>,
)