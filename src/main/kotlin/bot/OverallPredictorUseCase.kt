package bot

import bot.calibrator.Calibrator
import bot.predictor.GetPredictionPlayersGlickoUseCase
import bot.predictor.GetPredictionPlayersUseCase
import kotlinx.coroutines.*
import bot.predictor.PredictStats
import bot.predictor.Predictor
import model.*
import utils.JsonManager
import utils.round1
import utils.round2

var eloWinRate = 60
var glickoWinRate = 60
var winRateGap = 3
var eloSmall = 12.5
var eloBig = 25.0
var calibrationMatchCount = 10

//var maxWinRate = 0.0
//var maxPredicted = 0.0
//var maxWinRateEloWinRate = 0
//var maxWinRateGlickoWinRate = 0
//var maxWinRateWinRateGap = 0
//var maxWinRateEloSmall = 0.0
//var maxWinRateEloBig = 0.0
//var maxWinRateCalibrationMatchCount = 0
//
//fun main() {
//    runBlocking {
//        for (i1 in 1..5 step 1) {
//            winRateGap = i1
//            for (i2 in 5..20 step 5) {
//                calibrationMatchCount = i2
//                for (i3 in 10..25 step 5) {
//                    eloSmall = i3.toDouble()
//                    for (i4 in 20..50 step 10) {
//                        eloBig = i4.toDouble()
//                        for (i5 in 50..70 step 4) {
//                            eloWinRate = i5
//                            for (i6 in 50..70 step 4) {
//                                glickoWinRate = i6
//                                loadData()
//                                clearData()
//                                println("eloWinRate: $eloWinRate. glickoWinRate: $glickoWinRate. winRateGap: $winRateGap. eloSmall: $eloSmall. eloBig: $eloBig. matchCount: $calibrationMatchCount.")
//                                println("maxWinRate: $maxWinRate. maxPredicted: $maxPredicted")
//                                val result = OverallPredictorUseCase().execute()
//                                val winRate = result.winRate
//                                val predicted = result.predicted
//                                if (winRate > maxWinRate && predicted >= 50.0) {
//                                    maxWinRate = winRate
//                                    maxPredicted = predicted
//                                    maxWinRateEloWinRate = eloWinRate
//                                    maxWinRateGlickoWinRate = glickoWinRate
//                                    maxWinRateWinRateGap = winRateGap
//                                    maxWinRateEloSmall = eloSmall
//                                    maxWinRateEloBig = eloBig
//                                    maxWinRateCalibrationMatchCount = calibrationMatchCount
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        println("Max win rate: $maxWinRate")
//        println("Elo win rate: $maxWinRateEloWinRate")
//        println("Glicko win rate: $maxWinRateGlickoWinRate")
//        println("Win rate gap: $maxWinRateWinRateGap")
//        println("Elo small: $maxWinRateEloSmall")
//        println("Elo big: $maxWinRateEloBig")
//        println("Calibration match count: $maxWinRateCalibrationMatchCount")
//    }
//}

var streakStop = 1

fun main() {
    runBlocking {
        loadData()
        clearData()
        println(OverallPredictorUseCase().execute())

//        var i6 = 6
//        while (i6 < 25) {
//            loadData()
//            clearData()
//            streakStop = i6
//            println("streakStop ${streakStop} ${OverallPredictorUseCase().execute()}")
//            i6++
//        }
    }
}

private fun clearData() {
    val players = mutableSetOf<Player>()
    GetPredictionPlayersUseCase.predictionStats.clear()
    GetPredictionPlayersGlickoUseCase.predictionStats.clear()
    for (match in GlobalVars.matches) {
        players.addAll(match.firstTeamPlayers)
        players.addAll(match.secondTeamPlayers)
    }
    GlobalVars.players.clear()
    for (player in players) {
        GlobalVars.players[player.playerId] = (GlobalVars.players[player.playerId] ?: PlayerStats(player.playerId)).apply {
            name = player.name
            rating = 1400
            matchCount = 0
        }
    }
    println()
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

    private val predictionStatsTournaments = mutableMapOf<Long, PredictStats>()

    private var calibrationPercentage: Int = 0
    val predictionStatsTeams = mutableMapOf<Long, PredictStats>()
    fun execute(
        _calibrationPercentage: Int = 0,
    ): String {
        val matches = GlobalVars.matches
        val tournaments = matches.groupBy { it.tournament.tournamentId }.keys
        for (tournament in tournaments) {
            predictionStatsTournaments[tournament] = PredictStats()
        }
        val teams = mutableSetOf<Long>()
        for (match in matches) {
            teams.add(match.firstTeam.teamId)
            teams.add(match.secondTeam.teamId)
        }
        for (team in teams) { //GlobalVars.teams.keys
            predictionStatsTeams[team] = PredictStats()
        }
        this.calibrationPercentage = _calibrationPercentage
        val calibrationMatchesCount = matches.size * calibrationPercentage / 100
        val calibrationMatches = matches.take(calibrationMatchesCount)
        val predictionMatches = matches.takeLast(matches.size - calibrationMatchesCount)

        for (match in calibrationMatches) {
            val prediction = predictionPattern.getPredictionByMatch(
                match = match,
            )
            PredictionPart.updateStats(prediction, match, null)
            calibrationPattern.execute(match)
        }

        val matchesWithCoefs = JsonManager.getAllMatchesWithCoef()

        var size = 0

        var count = 0
        var maxCount = 0
        var income = 1.0
        var overallIncome = 0.0
        var successStreaks = 0
        var maxMultiplier = 0.0
        var maxLoseStreak = 0
        var loseStreak = 0
        var avgLoseStreak = mutableListOf<Int>()
        var avgCount = mutableListOf<Int>()
        var avgIncome = mutableListOf<Double>()

        val previousMatches: MutableList<Match> = mutableListOf()
        for (match in predictionMatches) {
            val prediction = predictionPattern.getPredictionByMatch(
                match = match,
            )
            val willFirstTeamWin = prediction.willFirstTeamWin

//            val matchWithCoef = matchesWithCoefs.find { it.matchId == match.matchId } ?: match

//            val coef = if(willFirstTeamWin == true) matchWithCoef.firstTeamCoef else if(willFirstTeamWin == false) matchWithCoef.secondTeamCoef else 0.0

//            if(coef >= 1.0) {
//                size++
//            }

            if (prediction.type == PredictionType.INVALID_PLAYERS) {
                PredictionPart.updateStats(prediction, match, null)
                calibrationPattern.execute(match)
                continue
            }

            if (willFirstTeamWin == null) {
                predictionStatsTournaments[match.tournament.tournamentId]!!.unpredicted++
                predictionStatsTeams[match.firstTeam.teamId]!!.unpredicted++
                predictionStatsTeams[match.secondTeam.teamId]!!.unpredicted++
                PredictionPart.updateStats(prediction, match, null)
            } else if (isMatchPredicted(match, willFirstTeamWin)) {
                    predictionStatsTeams[match.firstTeam.teamId]!!.wins++
                    predictionStatsTeams[if (match.hasFirstTeamWon) match.firstTeam.teamId else match.secondTeam.teamId]!!.winPredicted++
                    predictionStatsTeams[if (!match.hasFirstTeamWon) match.firstTeam.teamId else match.secondTeam.teamId]!!.losePredicted++
                    predictionStatsTeams[match.secondTeam.teamId]!!.wins++
                    predictionStatsTournaments[match.tournament.tournamentId]!!.wins++

                    PredictionPart.updateStats(prediction, match, null)
//                if(coef >= 1.0) {
//                    PredictionPart.updateStats(prediction, match, coef - 1)
//                    predictionStatsTournaments[match.tournament.tournamentId]!!.income += coef - 1
//
//                    predictionStatsTournaments[match.tournament.tournamentId]!!.coefs += coef
//
//                    count++
//                    income *= coef
//                    if (maxMultiplier < income) {
//                        maxMultiplier = income
//                    }
//                    if (maxCount < count) {
//                        maxCount = count
//                    }
////                     if (income > streakStop) {
//                    if (income > streakStop && count % streakStop == 0) {
//                        avgLoseStreak.add(loseStreak)
//                        avgCount.add(count)
//                        successStreaks++
//                        overallIncome += income - 1
//                        avgIncome.add(income - 1)
//                        count = 0
//                        income = 1.0
//                        loseStreak = 0
//                    }
//                }
            } else {
                    predictionStatsTeams[match.firstTeam.teamId]!!.fails++
                    predictionStatsTeams[match.secondTeam.teamId]!!.fails++
                    predictionStatsTeams[if (match.hasFirstTeamWon) match.firstTeam.teamId else match.secondTeam.teamId]!!.winUnpredicted++
                    predictionStatsTeams[if (!match.hasFirstTeamWon) match.firstTeam.teamId else match.secondTeam.teamId]!!.loseUnpredicted++
                    predictionStatsTournaments[match.tournament.tournamentId]!!.fails++

                    PredictionPart.updateStats(prediction, match, null)
//                if(coef >= 1.0) {
//                    PredictionPart.updateStats(prediction, match, -1.0)
//                    predictionStatsTournaments[match.tournament.tournamentId]!!.income += -1.0
//
//                    count = 0
//                    income = 1.0
//                    overallIncome -= 1
//                    loseStreak++
//                    if (maxLoseStreak < loseStreak) {
//                        maxLoseStreak = loseStreak
//                    }
//                }
            }
            calibrationPattern.execute(match)
            previousMatches.add(match)
        }
        println(size)

        BotRepository().updatePlayers()
        GetPredictionPlayersGlickoUseCase.updateFiles()
        GetPredictionPlayersUseCase.updateFiles()

        return analyzeData(predictionMatches, predictionStatsTournaments, predictionStatsTeams)
//        return "income " + overallIncome.round2().toString() + " streaks $successStreaks maxMultiplier ${maxMultiplier.round2()} avgLoseStreak ${avgLoseStreak.average().round2()} maxLoseStreak $maxLoseStreak avgIncome ${avgIncome.average().round2()} avgCount ${avgCount.average().round2()} maxCount $maxCount"
    }

    private fun analyzeData(
        predictions: List<Match>,
        statsTournament: Map<Long, PredictStats>,
        statsTeams: Map<Long, PredictStats>
    ): String {
        val stringBuffer = StringBuffer(500)
        stringBuffer.append("${predictions.size} матчей.\n")

        val tournamentInfo: MutableMap<String, Int> = mutableMapOf()
        val tournamentInfoSorted = tournamentInfo.toList().sortedByDescending { it.second }
        for (tournament in tournamentInfoSorted) {
            stringBuffer.append(tournament.first)
        }

        val sortedTeams = statsTeams.toList().sortedBy { it.second.income }
        for ((team, teamStats) in sortedTeams)
            stringBuffer.append(
                "\n(${GlobalVars.teams[team]?.name ?: team}) побед ${teamStats.wins} лузов ${teamStats.fails} " +
                        "винрейт ${teamStats.getWinRate()}%" +
                        " побед предсказано(${teamStats.winPredicted} ${teamStats.loseUnpredicted}) ${teamStats.getWinRateWinPredictions()}% поражений предсказано(${teamStats.losePredicted} ${teamStats.winUnpredicted}) ${teamStats.getWinRateLosePredictions()}%" +
                        " флетов: ${teamStats.income.round2()} средний кэф: ${
                            if (teamStats.coefs.isEmpty()) 0.0 else teamStats.coefs.average().round2()
                        }\n"
            )

        val predictionStats = PredictStats(
            wins = statsTournament.values.sumOf { it.wins },
            fails = statsTournament.values.sumOf { it.fails },
            unpredicted = statsTournament.values.sumOf { it.unpredicted },
            coefs = statsTournament.values.flatMap { it.coefs }.toMutableList(),
            income = statsTournament.values.sumOf { it.income }
        )

        stringBuffer.append(
            "(overall) win ${predictionStats.wins} fail ${predictionStats.fails} " +
                    "wr ${predictionStats.getWinRate()}% predicted ${predictionStats.getPredictedPercentage()}%" +
                    " income: ${predictionStats.income.round1()}% avg coef: ${predictionStats.coefs.average()}\n"
        )
        val sorted = statsTournament.toList().sortedBy { it.second.income }
        for ((tournamentId, tournamentsStats) in sorted) {
            val tournament = GlobalVars.tournaments[tournamentId] ?: continue
            stringBuffer.append(
                "\n(${tournament.name} id=${tournament.tournamentId} win ${tournamentsStats.wins} fail ${tournamentsStats.fails} " +
                        "wr ${tournamentsStats.getWinRate()}% predicted ${tournamentsStats.getPredictedPercentage()}%" +
                        " income: ${tournamentsStats.income.round1()}% avg coef: ${tournamentsStats.coefs.average()}\n"
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
//        return "wr: ${predictionStats.getWinRate()} income:  ${predictionStats.income.round2()} wins ${predictionStats.wins} fails ${predictionStats.fails} avgCoef ${
//            try {
//                predictionStats.coefs.average().round2()
//            } catch (e: Exception) {
//                "NaN"
//            }
//        }  predicted ${predictionStats.getPredictedPercentage()}%"
        return stringBuffer.toString() + "income:  ${predictStats.income} wins ${predictStats.wins} fails ${predictStats.fails}"
    }

    private fun isMatchPredicted(
        match: Match,
        willFirstTeamWin: Boolean?
    ): Boolean {
        return willFirstTeamWin == match.hasFirstTeamWon
    }
}