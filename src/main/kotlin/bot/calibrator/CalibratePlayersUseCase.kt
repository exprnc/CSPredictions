package bot.calibrator

import bot.GlobalVars
import bot.eloBig
import bot.eloSmall
import model.Match
import model.PlayerStats
import bot.validator.Validator
import kotlin.math.pow

class CalibratePlayersUseCase {

    fun execute(match: Match) {
        val firstTeamValue = getTeamValue(match.firstTeamPlayers.map { it.playerId })
        val secondTeamValue = getTeamValue(match.secondTeamPlayers.map { it.playerId })
        changeRating(match, match.firstTeamPlayers.map { it.playerId }, secondTeamValue, match.hasFirstTeamWon)
        changeRating(match, match.secondTeamPlayers.map { it.playerId }, firstTeamValue, !match.hasFirstTeamWon)
    }

    private fun changeRating(
        match: Match,
        players: List<Long>,
        enemyTeamRating: Int,
        hasTeamWon: Boolean
    ) {
        for (player in players) {
            val playerItem = (GlobalVars.players[player] ?: PlayerStats(player)).apply {
                rating = getNewValue(
                    match,
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
        match: Match,
        player: PlayerStats,
        hasTeamWon: Boolean,
        enemyTeamRating: Int
    ): Int {
        val maxDiff = 400.0
        var maxChange: Double = when {
            Validator.isCalibrated(playerId = player.playerId) -> eloSmall
            else -> eloBig
        }

        maxChange *= if(match.bestOf == 5) 7.0 else if(match.getTier() == 1) 3.0 else if(match.getTier() == 2) 2.0 else 1.0

        val result = if (hasTeamWon) 1 else 0
        val ea = 1 / (1 + 10.0.pow((enemyTeamRating - player.rating) * 1.0 / maxDiff))
        return (player.rating + maxChange * (result - ea)).toInt()
    }

    private fun getTeamValue(players: List<Long>): Int {
        return players.sumOf { GlobalVars.players[it]?.rating ?: 1400 } / 5
    }
}
