package bot

import bot.IncomeHandlerV2.Ladder.Companion.LADDER_INCOME
import bot.IncomeHandlerV2.Parlay.Companion.PARLAY_STREAK
import bot.validator.Validator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import model.Prediction
import utils.*
import java.lang.reflect.Type

object IncomeHandlerV2 {
    private val fileManager = FileManager
    private val gson = Gson()
    private val fileName = "income"
    private const val BASE_MULTIPLIER = 1.0
    val type: Type = object : TypeToken<MutableList<IncomeMatch>?>() {}.type
    var matches: MutableList<IncomeMatch> = try {
        gson.fromJson<MutableList<IncomeMatch>?>(fileManager.readFromFile(fileName), type) ?: mutableListOf()
    } catch (e: Exception) {
        println("can't load income file ${e.stackTraceToString()}")
        mutableListOf()
    }

    fun update() {
        updateStatistics()
        updateFile()
    }

    private fun updateStatistics() {
        val buffer = StringBuffer(1000)
        if (matches.isEmpty()) return

        val validTournaments = matches.filter { it.isValid.orDefault() && it.hasRadiantWon != null }.groupBy { it.leagueId }
        appendLeague("Overall", validTournaments.values.flatten(), buffer)

//        chat.updateStatistics(buffer.toString())
    }

    private fun appendLeague(name: String, matches: List<IncomeMatch>, buffer: StringBuffer) {
        buffer.append("$name:\n")
        appendFlatStrategy(matches, buffer)
        appendLadderStrategy(matches, buffer)
        appendParlayStrategy(matches, buffer)
        buffer.append("\n")
    }

    private fun appendFlatStrategy(matches: List<IncomeMatch>, buffer: StringBuffer) {
        var winCount = 0
        var validCount = 0
        var unpredictableCount = 0
        for (match in matches) {
            if (match.isValid.orDefault()) validCount++
            if (match.willRadiantWin == null) unpredictableCount++
            if (match.willRadiantWin == match.hasRadiantWon) winCount++
        }
        val winRate = getPercentage(winCount, validCount - unpredictableCount)
        val predicted = getPercentage(validCount - unpredictableCount, validCount)

        val overallIncome = matches.fold(0.0) { acc, next -> acc + next.getIncomeFlat() }

        val avgCoef = getAvgCoef(matches)

        val bankSign = if (overallIncome > 0) "+" else ""
        buffer.append("Flat strategy:\n")
        buffer.append("Flat: $bankSign${(overallIncome).round3()} $avgCoef\n")
        buffer.append("WinRate: $winRate% | Predicted: $predicted%\n")
    }

    private fun appendLadderStrategy(matches: List<IncomeMatch>, buffer: StringBuffer) {
        var currentMultiplier = 1.0
        var maxMultiplier = 0.0

        var avgIncome = mutableListOf<Double>()
        var overallIncome = 0.0
        var successStreaks = 0

        var maxLoseStreak = 0
        var loseStreak = 0
        var avgLoseStreak = mutableListOf<Int>()

        for (match in matches) {
            val strategy = match.ladderStrategy ?: continue
            if (match.willRadiantWin == null || match.hasReturned || match.bestOdd == null) {

            } else if (match.willRadiantWin == match.hasRadiantWon) {
                currentMultiplier = strategy.multiplier
                if (maxMultiplier < currentMultiplier) {
                    maxMultiplier = currentMultiplier
                }
                if (strategy.isFinal) {
                    val income = currentMultiplier * match.bestOdd.second - 1
                    avgLoseStreak.add(loseStreak)
                    successStreaks++
                    overallIncome += income
                    avgIncome.add(income)
                    loseStreak = 0
                }
            } else {
                overallIncome -= 1
                loseStreak++
                if (maxLoseStreak < loseStreak) {
                    maxLoseStreak = loseStreak
                }
            }
        }
        val avgCoef = if (avgIncome.isEmpty()) "" else "| avg income: ${avgIncome.average().round3()}"
        val avgLoseStreakStr = if (avgLoseStreak.isEmpty()) "" else "avg ${avgLoseStreak.average().round1()}"
        val bankSign = if (overallIncome > 0) "+" else ""
        buffer.append("Ladder strategy (until ${LADDER_INCOME} flats):\n")
        buffer.append("Flat: $bankSign${(overallIncome).round3()} $avgCoef\n")
        buffer.append("win streaks: $successStreaks | max bet: ${maxMultiplier}\n")
        buffer.append("lose streaks: max $maxLoseStreak $avgLoseStreakStr\n")
    }

    private fun appendParlayStrategy(matches: List<IncomeMatch>, buffer: StringBuffer) {
        var currentMultiplier = 1.0
        var maxMultiplier = 0.0

        var avgIncome = mutableListOf<Double>()
        var overallIncome = 0.0
        var successStreaks = 0

        var maxLoseStreak = 0
        var loseStreak = 0
        var avgLoseStreak = mutableListOf<Int>()

        for (match in matches) {
            val strategy = match.parlayStrategy ?: continue
            if (match.willRadiantWin == null || match.hasReturned || match.bestOdd == null) {

            } else if (match.willRadiantWin == match.hasRadiantWon) {
                currentMultiplier = strategy.multiplier
                if (maxMultiplier < currentMultiplier) {
                    maxMultiplier = currentMultiplier
                }
                if (strategy.isFinal) {
                    val income = currentMultiplier * match.bestOdd.second - 1
                    avgLoseStreak.add(loseStreak)
                    successStreaks++
                    overallIncome += income
                    avgIncome.add(income)
                    loseStreak = 0
                }
            } else {
                overallIncome -= 1
                loseStreak++
                if (maxLoseStreak < loseStreak) {
                    maxLoseStreak = loseStreak
                }
            }
        }
        val avgCoef = if (avgIncome.isEmpty()) "" else "| avg income: ${avgIncome.average().round3()}"
        val avgLoseStreakStr = if (avgLoseStreak.isEmpty()) "" else "avg ${avgLoseStreak.average().round1()}"
        val bankSign = if (overallIncome > 0) "+" else ""
        buffer.append("Parlay strategy (until ${PARLAY_STREAK} wins):\n")
        buffer.append("Flat: $bankSign${(overallIncome).round3()} $avgCoef\n")
        buffer.append("win streaks: $successStreaks | max bet: ${maxMultiplier}\n")
        buffer.append("lose streaks: max $maxLoseStreak $avgLoseStreakStr\n")
    }

    private fun getAvgCoef(matches: List<IncomeMatch>): String {
        var size = 0
        val incomeOverall = matches.fold(0.0) { acc, next ->
            val value = next.getCoef().orDefault()
            if (value > 1) size++
            acc + value
        }

        return if (size == 0) "" else "| Avg Coef: ${(incomeOverall / size).round3()}"
    }

    private fun getPercentage(arg1: Int, arg2: Int): Double {
        if (arg2 == 0) return 0.0
        return (arg1 * 1.0 / arg2 * 100).round2()
    }

    fun saveData(hasRadiantWon: Boolean, bet: IncomeMatch): IncomeMatch {
        val incomeMatch = bet.bestOdd?.second?.let { bestOdd ->
            bet.copy(
                hasRadiantWon = hasRadiantWon,
                ladderStrategy = bet.ladderStrategy?.apply {
                    if (hasRadiantWon == bet.willRadiantWin) {
                        isFinal = multiplier * bestOdd - 1 >= LADDER_INCOME
                    }
                },
                parlayStrategy = bet.parlayStrategy?.apply {
                    if (hasRadiantWon == bet.willRadiantWin) {
                        isFinal = count % PARLAY_STREAK == 0
                    }
                },
            )
        } ?: bet.copy(hasRadiantWon = hasRadiantWon)
        matches.add(incomeMatch)
        return incomeMatch
    }

    fun getLastMultiplierLadder(): Ladder {
        val matchesFiltered = matches.filter {
            it.willRadiantWin != null && it.hasReturned.not() && it.ladderStrategy != null
        }
        val lastAvailableMatch = matchesFiltered.lastOrNull {
            it.ladderStrategy?.inUse == false && it.ladderStrategy?.isFinal == false && it.willRadiantWin == it.hasRadiantWon
        }
        val lastMatchOdd = lastAvailableMatch?.bestOdd
        val lastStrategy = lastAvailableMatch?.ladderStrategy
        if (lastAvailableMatch == null || lastStrategy == null || lastMatchOdd == null) {
            return Ladder(BASE_MULTIPLIER)
        } else {
            val newMultiplier = (lastStrategy.multiplier * lastMatchOdd.second).round3()
            return Ladder(newMultiplier, usesMatchId = lastAvailableMatch.matchId)
        }
    }

    fun saveUsedStrategies(
        parlayMatchId: Long?,
        ladderMatchId: Long?,
    ) {
        var count = 0
        ladderMatchId?.let { matchId ->
            matches.find { it.matchId == matchId && it.ladderStrategy != null }?.ladderStrategy?.apply {
                inUse = true
                count++
            }
        }
        parlayMatchId?.let { matchId ->
            matches.find { it.matchId == matchId && it.parlayStrategy != null }?.parlayStrategy?.apply {
                inUse = true
                count++

            }
        }
        if (count > 0) {
            updateFile()
        }
    }

    fun getLastMultiplierParlay(): Parlay {
        val matchesFiltered = matches.filter {
            it.willRadiantWin != null && it.hasReturned.not() && it.parlayStrategy != null
        }
        val lastAvailableMatch = matchesFiltered.lastOrNull {
            it.parlayStrategy?.inUse == false && it.parlayStrategy?.isFinal == false && it.willRadiantWin == it.hasRadiantWon
        }
        val lastMatchOdd = lastAvailableMatch?.bestOdd
        val lastStrategy = lastAvailableMatch?.parlayStrategy
        if (lastAvailableMatch == null || lastStrategy == null || lastMatchOdd == null) {
            return Parlay(BASE_MULTIPLIER, 1)
        } else {
            val newMultiplier = (lastStrategy.multiplier * lastMatchOdd.second).round3()
            return Parlay(newMultiplier, lastStrategy.count + 1, usesMatchId = lastAvailableMatch.matchId)
        }
    }

    private fun updateFile() {
        fileManager.putToFile(fileName, gson.toJson(matches))
    }

    data class BookOdds(
        val firstTeamCoef: Double,
        val secondTeamCoef: Double
    )

    data class IncomeMatch(
        var books: Map<String, BookOdds>,
        val matchId: Long,
        val minCoef: Double = 1.0,
        val hasRadiantWon: Boolean?,
        val willRadiantWin: Boolean?,
        var hasReturned: Boolean = false,
        val leagueId: Long? = null,
        val isValid: Boolean? = null,
        var flatStrategy: Flat? = null,
        var ladderStrategy: Ladder? = null,
        var parlayStrategy: Parlay? = null,
        val bestOdd: Pair<String, Double>? = null
    ) {
        fun getCoef(): Double? {
            return books.getBestOdd(willRadiantWin)?.second?.takeIf { it >= minCoef && hasReturned.not() && willRadiantWin != null }
        }

        fun getIncomeFlat(): Double {
            val coef = getCoef()
            val income = when {
                coef == null -> 0.0
                willRadiantWin == hasRadiantWon -> (coef - 1)
                else -> -1.0
            }
            return income.round3()
        }

        fun getIncomeString(): String {
            val income = getIncomeFlat()

            return when {
                income > 0 -> {
                    var str = "Bot's strategies income:\n"
                    str += flatStrategy?.let { "Flat: +$income ✅\n" }.orDefault()
                    str += ladderStrategy?.let {
                        val nextMult = (it.multiplier * (income + 1)).round3()
                        val incomeCurrent = (nextMult - 1).round3()
                        val matchId = if (it.usesMatchId == null) "" else "(id ${it.usesMatchId})"
                        if (it.isFinal) {
                            "Ladder$matchId: +${incomeCurrent} ✅ (${incomeCurrent}/${LADDER_INCOME})\n"
                        } else {
                            "Ladder$matchId: ${it.multiplier} ➡️ ${nextMult} (${incomeCurrent}/${LADDER_INCOME})\n"
                        }
                    }.orDefault()
                    str += parlayStrategy?.let {
                        val nextMult = (it.multiplier * (income + 1)).round3()
                        val incomeCurrent = (nextMult - 1).round3()
                        val matchId = if (it.usesMatchId == null) "" else "(id ${it.usesMatchId})"
                        if (it.isFinal) {
                            "Parlay$matchId: +${incomeCurrent} ✅ (${it.count}/${PARLAY_STREAK})\n"
                        } else {
                            "Parlay$matchId: ${it.multiplier} ➡️ ${nextMult} (${it.count}/${PARLAY_STREAK})\n"
                        }
                    }.orDefault()
                    "$str✅✅✅✅✅✅"
                }

                income < 0 -> "Bot's income: $income\n🚫🚫🚫🚫🚫🚫"
                willRadiantWin == hasRadiantWon -> "\n✅✅✅✅✅✅"
                else -> "\n🚫🚫🚫🚫🚫🚫"
            }
        }

        fun getBotBetString(): String {
            if (bestOdd == null) return ""
            var strategies = "Betting amount (multiplier):\n"
            strategies += flatStrategy?.let { "Flat: ${it.multiplier}\n" }.orDefault()
            strategies += ladderStrategy?.let {
                val matchId = if (it.usesMatchId == null) "" else "(id ${it.usesMatchId})"
                "Ladder$matchId: ${it.multiplier} (${(it.multiplier - 1).round3()}/${LADDER_INCOME})\n"
            }.orDefault()
            strategies += parlayStrategy?.let {
                val matchId = if (it.usesMatchId == null) "" else "(id ${it.usesMatchId})"
                "Parlay$matchId: ${it.multiplier} (${it.count - 1}/${PARLAY_STREAK})\n"
            }.orDefault()
            return "Bot's bet: ${bestOdd.second} ${bestOdd.first}\n" + strategies.takeIf { hasRadiantWon == null }.orDefault()
        }

        companion object {

            fun Map<String, BookOdds>.getBestOdd(willFirstTeamWin: Boolean?): Pair<String, Double>? {
                if (willFirstTeamWin == null) return null
                val bestOdd = this.maxByOrNull { if (willFirstTeamWin) it.value.firstTeamCoef else it.value.secondTeamCoef } ?: return null
                return if (willFirstTeamWin) Pair(bestOdd.key, bestOdd.value.firstTeamCoef) else Pair(bestOdd.key, bestOdd.value.secondTeamCoef)
            }

            fun createFromOdds(
                match: Match,
                prediction: Prediction?
            ): IncomeMatch {
                val odds: Map<String, BookOdds> = mapOf() //TODO:
                val bestOdd = odds.getBestOdd(prediction?.willFirstTeamWin)

                val incomeMatch = IncomeMatch(
                    minCoef = 1.0,
                    books = odds,
                    matchId = match.matchId,
                    hasRadiantWon = null,
                    willRadiantWin = prediction?.willFirstTeamWin,
                    isValid = Validator.isValid(match),
                    leagueId = match.tournament.tournamentId
                )
                return if (bestOdd != null) {
                    val updated = incomeMatch.copy(
                        bestOdd = bestOdd,
                        flatStrategy = Flat(),
                        parlayStrategy = getLastMultiplierParlay(),
                        ladderStrategy = getLastMultiplierLadder(),
                    )
                    saveUsedStrategies(
                        parlayMatchId = updated.parlayStrategy?.usesMatchId,
                        ladderMatchId = updated.ladderStrategy?.usesMatchId,
                    )
                    updated
                } else {
                    incomeMatch
                }
            }
        }
    }


    data class Ladder(
        val multiplier: Double,
        var isFinal: Boolean = false,
        var inUse: Boolean = false,
        var usesMatchId: Long? = null
    ) {
        companion object {
            var LADDER_INCOME = 4
        }
    }

    data class Parlay(
        val multiplier: Double,
        val count: Int,
        var isFinal: Boolean = false,
        var inUse: Boolean = false,
        var usesMatchId: Long? = null
    ) {
        companion object {
            var PARLAY_STREAK = 2
        }
    }

    class Flat(val multiplier: Double = BASE_MULTIPLIER)
}
