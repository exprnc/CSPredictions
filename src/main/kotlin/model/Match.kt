package model

data class Match(
    val matchId: Long,
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
) {
    fun getTier() : Int {
        if(firstTeamRanking <= 0 || secondTeamRanking <= 0) return 3
        val averageRanking = (firstTeamRanking + secondTeamRanking) / 2.0
        return if(averageRanking <= 25) 1
        else if(averageRanking <= 30) 2
        else 3
    }
}