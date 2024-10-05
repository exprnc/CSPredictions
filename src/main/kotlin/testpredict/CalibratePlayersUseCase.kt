package testpredict

import model.Match
import kotlin.math.pow

class CalibratePlayersUseCase {

    fun execute(match: Match) {
        val firstTeamValue = getTeamValue(match.firstTeamPlayers.map { it.id })
        val secondTeamValue = getTeamValue(match.secondTeamPlayers.map { it.id })
        changeRating(match, match.firstTeamPlayers.map { it.id }, secondTeamValue, match.hasFirstTeamWon)
        changeRating(match, match.secondTeamPlayers.map { it.id }, firstTeamValue, !match.hasFirstTeamWon)
    }

    private fun changeRating(
        match: Match,
        players: List<Long>,
        enemyTeamRating: Int,
        hasTeamWon: Boolean
    ) {
        for (player in players) {
            val playerItem = (GlobalVars.players[player] ?: MemberInfo(player)).apply {
                rating = getNewValue(
                    match.tournament.id,
                    this,
                    hasTeamWon,
                    enemyTeamRating
                )
                matchCount++
            }
            GlobalVars.players[player] = playerItem
        }
    }

    private fun getNewValue(
        leagueId: Long,
        player: MemberInfo,
        hasTeamWon: Boolean,
        enemyTeamRating: Int
    ): Int {
        val maxDiff = 400.0
        var maxChange: Double = when {
            Validator.isCalibrated(playerId = player.playerId) -> eloSmall
            else -> eloBig
        }
        maxChange *= 1.0

        val result = if (hasTeamWon) 1 else 0
        val ea = 1 / (1 + 10.0.pow((enemyTeamRating - player.rating) * 1.0 / maxDiff))
        return (player.rating + maxChange * (result - ea)).toInt()
    }

    private fun getTeamValue(players: List<Long>): Int {
        return players.sumOf { GlobalVars.players[it]?.rating ?: 1400 } / 5
    }
}
