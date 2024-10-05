package testpredict

import model.Player
import utils.orDefault

object Validator {

    fun isValid(
        firstTeamPlayers: List<Long>,
        secondTeamPlayers: List<Long>,
    ): Boolean {
        return when {
            !isPlayersCalibrated(firstTeamPlayers, secondTeamPlayers) -> false
//            !isPlayersPro(firstTeamPlayers, secondTeamPlayers) -> false
            else -> true
        }
    }

    private const val MIN_PLAYERS = 1

    private fun isPlayersCalibrated(radiantPlayers: List<Long>, direPlayers: List<Long>): Boolean {
        return getCalibratedPlayersCount(radiantPlayers) >= MIN_PLAYERS  && getCalibratedPlayersCount(direPlayers) >= MIN_PLAYERS
    }

    fun isCalibrated(playerId: Long): Boolean = GlobalVars.players[playerId]?.matchCount.orDefault() >= calibrationMatchCount

    private fun getCalibratedPlayersCount(players: List<Long>): Int {
        return players.count { isCalibrated(it) }
    }

     fun isPlayersPro(radiantPlayers: List<Long>, direPlayers: List<Long>): Boolean {
        return getProPlayersCount(radiantPlayers) >= MIN_PLAYERS && getProPlayersCount(direPlayers) >= MIN_PLAYERS
    }

    private fun getProPlayersCount(players: List<Long>): Int {
        return players.count { GlobalVars.players[it]?.name.orEmpty().isNotEmpty() }
    }
}
