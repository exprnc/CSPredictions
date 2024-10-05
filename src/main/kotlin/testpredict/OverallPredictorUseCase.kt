package testpredict

import kotlinx.coroutines.*
import model.Match
import utils.round1
import utils.round2

var eloWinRate = 52
var glickoWinRate = 52
var winRateGap = 1
var eloSmall = 10.0
var eloBig = 20.0
var calibrationMatchCount = 5

var maxWinRate = 0.0
var maxPredicted = 0.0
var maxWinRateEloWinRate = 0
var maxWinRateGlickoWinRate = 0
var maxWinRateWinRateGap = 0
var maxWinRateEloSmall = 0.0
var maxWinRateEloBig = 0.0
var maxWinRateCalibrationMatchCount = 0

fun main() {
    runBlocking {
        for (i1 in 1..5 step 1) {
            winRateGap = i1
            for (i2 in 5..20 step 5) {
                calibrationMatchCount = i2
                for (i3 in 10..25 step 5) {
                    eloSmall = i3.toDouble()
                    for (i4 in 20..50 step 10) {
                        eloBig = i4.toDouble()
                        for (i5 in 50..70 step 4) {
                            eloWinRate = i5
                            for (i6 in 50..70 step 4) {
                                glickoWinRate = i6
                                loadData()
                                clearData()
                                println("eloWinRate: $eloWinRate. glickoWinRate: $glickoWinRate. winRateGap: $winRateGap. eloSmall: $eloSmall. eloBig: $eloBig. matchCount: $calibrationMatchCount.")
                                println("maxWinRate: $maxWinRate. maxPredicted: $maxPredicted")
                                val result = OverallPredictorUseCase().execute()
                                val winRate = result.winRate
                                val predicted = result.predicted
                                if (winRate > maxWinRate && predicted >= 50.0) {
                                    maxWinRate = winRate
                                    maxPredicted = predicted
                                    maxWinRateEloWinRate = eloWinRate
                                    maxWinRateGlickoWinRate = glickoWinRate
                                    maxWinRateWinRateGap = winRateGap
                                    maxWinRateEloSmall = eloSmall
                                    maxWinRateEloBig = eloBig
                                    maxWinRateCalibrationMatchCount = calibrationMatchCount
                                }
                            }
                        }
                    }
                }
            }
        }
        println("Max win rate: $maxWinRate")
        println("Elo win rate: $maxWinRateEloWinRate")
        println("Glicko win rate: $maxWinRateGlickoWinRate")
        println("Win rate gap: $maxWinRateWinRateGap")
        println("Elo small: $maxWinRateEloSmall")
        println("Elo big: $maxWinRateEloBig")
        println("Calibration match count: $maxWinRateCalibrationMatchCount")
    }
}

private fun clearData() {
    val set = mutableSetOf<Long>()
    for (match in GlobalVars.matches) {
        set.addAll(match.firstTeamPlayers.map { it.id })
        set.addAll(match.secondTeamPlayers.map { it.id })
    }
    GlobalVars.players.clear()
    for (playerId in set) {
        GlobalVars.players[playerId] = (GlobalVars.players[playerId] ?: MemberInfo(playerId)).apply {
            rating = 1400
            matchCount = 0
        }
    }
}

fun loadData() {
    val botRepository = BotRepository()
    botRepository.getAllPlayers()
    botRepository.getAllMatches()
}

class OverallPredictorUseCase {
    private val predictionPattern = Predictor()
    private val calibrationPattern = Calibrator()
    private val coefMap: MutableMap<String, PredictStats> = mutableMapOf()

    val predictionStatsLeagues = mutableMapOf<Long, PredictStats>()

    private var calibrationPercentage: Int = 0
    val predictionStatsTeams = mutableMapOf<Long, PredictStats>()
    fun execute(
        _calibrationPercentage: Int = 70,
    ): PredictResult {
        val leagues = GlobalVars.matches.groupBy { it.tournament.id }.keys
        for (league in leagues) {
            predictionStatsLeagues[league] = PredictStats()
        }
        val teams = mutableSetOf<Long>()
        for (match in GlobalVars.matches) {
            teams.add(match.firstTeam.id)
            teams.add(match.secondTeam.id)
        }
        for (team in teams) { //GlobalVars.teams.keys
            predictionStatsTeams[team] = PredictStats()
        }
        val matches = GlobalVars.matches.filter { GlobalVars.getTier(it) in 1..2 }.sortedBy { it.id }
        this.calibrationPercentage = _calibrationPercentage
        val calibrationMatchesCount = matches.size * calibrationPercentage / 100
        val calibrationMatches = matches.take(calibrationMatchesCount)
        val predictionMatches = matches.takeLast(matches.size - calibrationMatchesCount)


        for (match in calibrationMatches) {
            val prediction = predictionPattern.getPredictionBySimpleMatch(
                match = match,
            )
            PredictionPart.updateStats(prediction, match, null, GlobalVars.getTier(match))
            calibrationPattern.execute(match)
        }

        val previousMatches: MutableList<Match> = mutableListOf()
        for (match in predictionMatches) {
            val prediction = predictionPattern.getPredictionBySimpleMatch(
                match = match,
            )
            val willRadiantWin = prediction.willFirstTeamWin

            if (prediction.type == PredictionType.INVALID_PLAYERS) {
                PredictionPart.updateStats(prediction, match, null, GlobalVars.getTier(match))
                calibrationPattern.execute(match)
                continue
            }

            if (willRadiantWin == null) {
                predictionStatsLeagues[match.tournament.id]!!.unpredicted++
                predictionStatsTeams[match.firstTeam.id]!!.unpredicted++
                predictionStatsTeams[match.secondTeam.id]!!.unpredicted++
            } else if (isMatchPredicted(match, willRadiantWin)) {
                predictionStatsTeams[match.firstTeam.id]!!.wins++
                predictionStatsTeams[if (match.hasFirstTeamWon) match.firstTeam.id else match.secondTeam.id]!!.winPredicted++
                predictionStatsTeams[if (!match.hasFirstTeamWon) match.firstTeam.id else match.secondTeam.id]!!.losePredicted++
                predictionStatsTeams[match.secondTeam.id]!!.wins++
                predictionStatsLeagues[match.tournament.id]!!.wins++
            } else {
                predictionStatsTeams[match.firstTeam.id]!!.fails++
                predictionStatsTeams[match.secondTeam.id]!!.fails++
                predictionStatsTeams[if (match.hasFirstTeamWon) match.firstTeam.id else match.secondTeam.id]!!.winUnpredicted++
                predictionStatsTeams[if (!match.hasFirstTeamWon) match.firstTeam.id else match.secondTeam.id]!!.loseUnpredicted++
                predictionStatsLeagues[match.tournament.id]!!.fails++
            }
            PredictionPart.updateStats(prediction, match, null, GlobalVars.getTier(match))
            calibrationPattern.execute(match)
            previousMatches.add(match)
        }
        return analyzeData(predictionMatches, predictionStatsLeagues, predictionStatsTeams)
    }

    private fun analyzeData(predictions: List<Match>, statsLeague: Map<Long, PredictStats>, statsTeams: Map<Long, PredictStats>): PredictResult {
        val stringBuffer = StringBuffer(500)
        stringBuffer.append("${predictions.size} матчей.\n")

        val leaguesInfo: MutableMap<String, Int> = mutableMapOf()
        val leaguesInfoSorted = leaguesInfo.toList().sortedByDescending { it.second }
        for (league in leaguesInfoSorted) {
            stringBuffer.append(league.first)
        }

        val sortedTeams = statsTeams.toList().sortedBy { it.second.income }
        for ((team, teamStats) in sortedTeams)
            stringBuffer.append(
                "\n(${GlobalVars.teams[team]?.name ?: team}) побед ${teamStats.wins} лузов ${teamStats.fails} " +
                    "винрейт ${teamStats.getWinRate()}%" +
                    " побед предсказано(${teamStats.winPredicted} ${teamStats.loseUnpredicted}) ${teamStats.getWinRateWinPredictions()}% поражений предсказано(${teamStats.losePredicted} ${teamStats.winUnpredicted}) ${teamStats.getWinRateLosePredictions()}%" +
                    " флетов: ${teamStats.income.round2()} средний кэф: ${if (teamStats.coefs.isEmpty()) 0.0 else teamStats.coefs.average().round2()}\n"
            )

        val predictionStats = PredictStats(
            wins = statsLeague.values.sumOf { it.wins },
            fails = statsLeague.values.sumOf { it.fails },
            unpredicted = statsLeague.values.sumOf { it.unpredicted },
            coefs = statsLeague.values.flatMap { it.coefs }.toMutableList(),
            income = statsLeague.values.sumOf { it.income }
        )

        stringBuffer.append(
            "(overall) win ${predictionStats.wins} fail ${predictionStats.fails} " +
                "wr ${predictionStats.getWinRate()}% predicted ${predictionStats.getPredictedPercentage()}%" +
                " income: ${predictionStats.income.round1()}% avg coef: ${predictionStats.coefs.average()}\n"
        )
        val sorted = statsLeague.toList().sortedBy { it.second.income }
        for ((leagueId, leaguesStats) in sorted) {
            val league = GlobalVars.tournaments[leagueId] ?: continue
            stringBuffer.append(
                "\n(${league.name} id=${league.id} win ${leaguesStats.wins} fail ${leaguesStats.fails} " +
                    "wr ${leaguesStats.getWinRate()}% predicted ${leaguesStats.getPredictedPercentage()}%" +
                    " income: ${leaguesStats.income.round1()}% avg coef: ${leaguesStats.coefs.average()}\n"
            )
        }

        val coefMapSorted = coefMap.toList().sortedBy { it.first }
        for ((key, value) in coefMapSorted) {
            stringBuffer.append("coef $key: income: ${value.income.round2()} wins: ${value.wins} fails: ${value.fails}\n")
        }
        val predictStats = PredictStats()
        coefMapSorted.forEach {
            predictStats.income += it.second.income
            predictStats.wins += it.second.wins
            predictStats.fails += it.second.fails
        }
        return PredictResult(
            winRate = predictionStats.getWinRate(),
            predicted = predictionStats.getPredictedPercentage(),
            wins = predictionStats.wins,
            fails = predictionStats.fails
        )
//        return "wr: ${predictionStats.getWinRate()} income:  ${predictionStats.income.round2()} wins ${predictionStats.wins} fails ${predictionStats.fails} avgCoef ${
//            try {
//                predictionStats.coefs.average().round2()
//            } catch (e: Exception) {
//                "NaN"
//            }
//        }  predicted ${predictionStats.getPredictedPercentage()}%"
//        return stringBuffer.toString() + "income:  ${predictStats.income} wins ${predictStats.wins} fails ${predictStats.fails}"
    }

    private fun isMatchPredicted(
        match: Match,
        willRadiantWin: Boolean?
    ): Boolean {
        return willRadiantWin == match.hasFirstTeamWon
    }
}

data class PredictResult(
    val winRate: Double,
    val predicted: Double,
    val wins: Int,
    val fails: Int
)