package testpredict

import com.google.gson.Gson
import model.Match

data class PredictionPart(
    val willFirstTeamWin: Boolean,
    val diff: Double,
    val winRate: Double,
) {
    companion object {
        val gson = Gson()

        fun getInterval(diff: Int, max: Int, step: Int): Int {
            return when {
                diff <= -max -> -max / step
                diff >= max -> max / step
                diff in -step + 1 until step -> 0
                else -> diff / step
            }
        }

        fun getWinRate(value: Double, predictionStats: Map<Int, PredictStats>, max: Int, step: Int): Double {
            var interval = getInterval(value.toInt(), max, step)
            while (true) {
                val stats = predictionStats[interval]?.takeIf { it.wins + it.fails >= 30 }
                if (stats == null) {
                    interval--
                    if (interval < 0) {
                        return 0.0
                    }
                } else {
                    return stats.getWinRate()
                }
            }
        }

        fun updateStats(
            prediction: Prediction,
            match: Match,
            income: Double?,
            tier: Int
        ) {
            if (tier !in 1..2) return
            GetPredictionPlayersUseCase.updateStats(prediction.team, match, income)
            GetPredictionPlayersGlickoUseCase.updateStats(prediction.teamGlicko, match, income)
        }
    }
}