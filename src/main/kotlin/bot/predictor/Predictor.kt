package bot.predictor

import bot.eloWinRate
import bot.glickoWinRate
import model.Match
import model.Prediction
import model.PredictionPart
import model.PredictionType
import bot.winRateGap
import bot.validator.Validator
import utils.orDefault
import utils.round2
import kotlin.math.absoluteValue

class Predictor {

    fun getPredictionByMatch(
        match: Match,
    ): Prediction {

        val teamPrediction: PredictionPart = GetPredictionPlayersUseCase.execute(match)
        val teamGlickoPrediction: PredictionPart = GetPredictionPlayersGlickoUseCase.execute(match)

        if (!Validator.isValid(
                match.firstTeamPlayers.map { it.playerId },
                match.secondTeamPlayers.map { it.playerId })
        ) {
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
        teamPrediction: PredictionPart?,
        teamGlickoPrediction: PredictionPart?,
    ): PredictionPart? {
        val firstTeamPatterns = mutableListOf<PredictionPart?>()
        val secondTeamPatterns = mutableListOf<PredictionPart?>()

        addToList(firstTeamPatterns, secondTeamPatterns, teamPrediction)
        addToList(firstTeamPatterns, secondTeamPatterns, teamGlickoPrediction)

        val firstTeamMax = firstTeamPatterns.maxByOrNull { it?.winRate.orDefault() }
        val secondTeamMax = secondTeamPatterns.maxByOrNull { it?.winRate.orDefault() }
        if (firstTeamMax == null && secondTeamMax == null)
            return null

        val diff = firstTeamMax?.winRate.orDefault() - secondTeamMax?.winRate.orDefault()
        return if (diff.absoluteValue < winRateGap) {
            null
        } else if (diff > 0) {
            firstTeamMax
        } else {
            secondTeamMax
        }
    }


    private fun addToList(
        firstTeamPatterns: MutableList<PredictionPart?>,
        secondTeamPatterns: MutableList<PredictionPart?>,
        prediction: PredictionPart?,
    ) {
        if (prediction?.winRate.orDefault() > 0) {
            when (prediction?.willFirstTeamWin) {
                true -> firstTeamPatterns.add(prediction)
                false -> secondTeamPatterns.add(prediction)
                else -> Unit
            }
        }
    }
}