package testpredict

import model.Match
import utils.orDefault
import utils.round2
import kotlin.math.absoluteValue

class Predictor {

    fun getPredictionBySimpleMatch(
        match: Match,
    ): Prediction {
        val teamPrediction: PredictionPart = GetPredictionPlayersUseCase.execute(match)
        val teamGlickoPrediction: PredictionPart = GetPredictionPlayersGlickoUseCase.execute(match)

        if (!Validator.isValid(match.firstTeamPlayers.map { it.id }, match.secondTeamPlayers.map { it.id }) || GlobalVars.getTier(match) !in 1..2) {
            return Prediction(
                willFirstTeamWin = null,
                team = teamPrediction,
                teamGlicko = teamGlickoPrediction,
                minCoef = null,
                bestPrediction = null,
                type = PredictionType.INVALID_PLAYERS
            )
        }

        val bestPattern = getBestPattern(
            match,
            teamPrediction.takeIf { it.winRate > eloWinRate },
            teamGlickoPrediction.takeIf { it.winRate > glickoWinRate },
        )

        if (bestPattern == null) {
            return Prediction(
                willFirstTeamWin = null,
                team = teamPrediction,
                teamGlicko = teamGlickoPrediction,
                bestPrediction = null,
                minCoef = null,
                type = PredictionType.UNPREDICTABLE
            )
        }

        return Prediction(
            willFirstTeamWin = bestPattern.willFirstTeamWin,
            team = teamPrediction,
            teamGlicko = teamGlickoPrediction,
            minCoef = getMinCoef(bestPattern.winRate),
            bestPrediction = bestPattern,
            type = PredictionType.NORMAL
        )
    }

    private fun getMinCoef(winRate: Double): Double {
        return (1 / winRate * 100).round2()
    }

    private fun getBestPattern(
        match: Match,
        teamPrediction: PredictionPart?,
        teamGlickoPrediction: PredictionPart?,
    ): PredictionPart? {
        val radiantTeamPatterns = mutableListOf<PredictionPart?>()
        val direTeamPatterns = mutableListOf<PredictionPart?>()

        addToList(radiantTeamPatterns, direTeamPatterns, teamPrediction)
        addToList(radiantTeamPatterns, direTeamPatterns, teamGlickoPrediction)

        val radiantMax = radiantTeamPatterns.maxByOrNull { it?.winRate.orDefault() }
        val direMax = direTeamPatterns.maxByOrNull { it?.winRate.orDefault() }
        if (radiantMax == null && direMax == null)
            return null

        val diff = radiantMax?.winRate.orDefault() - direMax?.winRate.orDefault()
        return if (diff.absoluteValue < winRateGap) {
            null
        } else if (diff > 0) {
            radiantMax
        } else {
            direMax
        }
    }


    private fun addToList(
        radiantTeamPatterns: MutableList<PredictionPart?>,
        direTeamPatterns: MutableList<PredictionPart?>,
        prediction: PredictionPart?,
    ) {
        if (prediction?.winRate.orDefault() > 0) {
            when (prediction?.willFirstTeamWin) {
                true -> radiantTeamPatterns.add(prediction)
                false -> direTeamPatterns.add(prediction)
                else -> Unit
            }
        }
    }
}