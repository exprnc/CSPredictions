package bot.predictor

import utils.round2

class PredictStats(
    var wins: Int = 0,
    var fails: Int = 0,
    var unpredicted: Int = 0,
    var losePredicted: Int = 0,
    var winPredicted: Int = 0,
    var loseUnpredicted: Int = 0,
    var winUnpredicted: Int = 0,
    var income: Double = 0.0,
    val coefs: MutableList<Double> = mutableListOf()
) {
    fun getWinRate(): Double {
        if (wins + fails == 0) return 0.0
        return (wins * 1.0 / (wins + fails) * 100).round2()
    }

    fun getWinRateWinPredictions(): Double {
        if (winPredicted + loseUnpredicted == 0) return 0.0
        return (winPredicted * 1.0 / (winPredicted + loseUnpredicted) * 100).round2()
    }

    fun getWinRateLosePredictions(): Double {
        if (winUnpredicted + losePredicted == 0) return 0.0
        return (losePredicted * 1.0 / (losePredicted + winUnpredicted) * 100).round2()
    }

    fun getPredictedPercentage(): Double {
        val predicted= wins + fails
        if (predicted + unpredicted == 0) return 0.0
        return (predicted * 1.0 / (predicted + unpredicted) * 100).round2()
    }

}
