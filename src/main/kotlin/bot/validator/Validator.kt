package bot.validator

import bot.GlobalVars
import bot.calibrationMatchCount
import utils.orDefault

object Validator {

    fun isValid(
        firstTeamPlayers: List<Long>,
        secondTeamPlayers: List<Long>,
    ): Boolean {
        return when {
            !isPlayersCalibrated(firstTeamPlayers, secondTeamPlayers) -> false
            else -> true
        }
    }

    private const val MIN_PLAYERS = 1

    private fun isPlayersCalibrated(firstTeamPlayers: List<Long>, secondTeamPlayers: List<Long>): Boolean {
        return getCalibratedPlayersCount(firstTeamPlayers) >= MIN_PLAYERS  && getCalibratedPlayersCount(secondTeamPlayers) >= MIN_PLAYERS
    }

    fun isCalibrated(playerId: Long): Boolean = GlobalVars.players[playerId]?.matchCount.orDefault() >= calibrationMatchCount

    private fun getCalibratedPlayersCount(players: List<Long>): Int {
        return players.count { isCalibrated(it) }
    }
}
