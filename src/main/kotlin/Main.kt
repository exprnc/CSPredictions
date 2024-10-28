import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.*
import utils.JsonManager
import java.io.File
import java.io.FileWriter

fun main() {
    val hltvMatches = JsonManager.getAllMatches()
    val eGamersMatches = getAllEGamersMatches()

    val matchesWithCoefs = mutableListOf<Match>()

    hltvMatches.forEach { hltvMatch ->
        eGamersMatches.forEach { eGamersMatch ->

            if((hltvMatch.firstTeam.name.contains(eGamersMatch.firstTeamName, true) || eGamersMatch.firstTeamName.contains(hltvMatch.firstTeam.name, true)) && (hltvMatch.secondTeam.name.contains(eGamersMatch.secondTeamName, true) || eGamersMatch.secondTeamName.contains(hltvMatch.secondTeam.name, true))
                && hltvMatch.firstTeamScore == eGamersMatch.firstTeamScore && hltvMatch.secondTeamScore == eGamersMatch.secondTeamScore
                && hltvMatch.bestOf == eGamersMatch.bestOf
                && (hltvMatch.tournament.name.contains(eGamersMatch.tournamentName, true) || eGamersMatch.tournamentName.contains(hltvMatch.tournament.name, true))) {
                matchesWithCoefs.add(
                    Match(
                        matchId = hltvMatch.matchId,
                        bestOf = hltvMatch.bestOf,
                        tournament = hltvMatch.tournament,
                        firstTeam = hltvMatch.firstTeam,
                        secondTeam = hltvMatch.secondTeam,
                        firstTeamRanking = hltvMatch.firstTeamRanking,
                        secondTeamRanking = hltvMatch.secondTeamRanking,
                        firstTeamCoef = eGamersMatch.firstTeamCoef,
                        secondTeamCoef = eGamersMatch.secondTeamCoef,
                        firstTeamScore = hltvMatch.firstTeamScore,
                        secondTeamScore = hltvMatch.secondTeamScore,
                        hasFirstTeamWon = hltvMatch.hasFirstTeamWon,
                        firstTeamPlayers = hltvMatch.firstTeamPlayers,
                        secondTeamPlayers = hltvMatch.secondTeamPlayers
                    )
                )
            } else if ((hltvMatch.firstTeam.name.contains(eGamersMatch.secondTeamName, true) || eGamersMatch.secondTeamName.contains(hltvMatch.firstTeam.name, true)) && (hltvMatch.secondTeam.name.contains(eGamersMatch.firstTeamName, true) || eGamersMatch.firstTeamName.contains(hltvMatch.secondTeam.name, true))
                && hltvMatch.firstTeamScore == eGamersMatch.secondTeamScore && hltvMatch.secondTeamScore == eGamersMatch.firstTeamScore
                && hltvMatch.bestOf == eGamersMatch.bestOf
                && (hltvMatch.tournament.name.contains(eGamersMatch.tournamentName, true) || eGamersMatch.tournamentName.contains(hltvMatch.tournament.name, true))) {
                matchesWithCoefs.add(
                    Match(
                        matchId = hltvMatch.matchId,
                        bestOf = hltvMatch.bestOf,
                        tournament = hltvMatch.tournament,
                        firstTeam = hltvMatch.firstTeam,
                        secondTeam = hltvMatch.secondTeam,
                        firstTeamRanking = hltvMatch.firstTeamRanking,
                        secondTeamRanking = hltvMatch.secondTeamRanking,
                        firstTeamCoef = eGamersMatch.secondTeamCoef,
                        secondTeamCoef = eGamersMatch.firstTeamCoef,
                        firstTeamScore = hltvMatch.firstTeamScore,
                        secondTeamScore = hltvMatch.secondTeamScore,
                        hasFirstTeamWon = hltvMatch.hasFirstTeamWon,
                        firstTeamPlayers = hltvMatch.firstTeamPlayers,
                        secondTeamPlayers = hltvMatch.secondTeamPlayers
                    )
                )
            }
        }
    }

    val json = Gson().toJson(matchesWithCoefs)
    FileWriter("matchesWithCoefs.json").use { it.write(json) }


}

fun getAllEGamersMatches() : List<EGamersMatch> {
    val json = File("eGamersMatches.json").readText()
    val type = object : TypeToken<List<EGamersMatch>>() {}.type
    return Gson().fromJson(json, type)
}