package bot.predictor

import model.Match
import model.PredictionPart
import utils.FileManager
import utils.orDefault

interface GetPredictionUseCase {

    val step: Int
    val max: Int
    val fileName: String
    val predictionStats: MutableMap<Int, PredictStats>

    fun updateStats(
        predictionPart: PredictionPart?,
        match: Match,
        income: Double?
    ) {
        if (predictionPart == null || predictionPart.diff <= step) return
        val diff = predictionPart.diff.toInt()
        val hasBettingWon = predictionPart.willFirstTeamWin == match.hasFirstTeamWon

        val interval = PredictionPart.getInterval(diff, max, step)
        if (predictionStats[interval] == null) predictionStats[interval] = PredictStats()
        predictionStats[interval]!!.apply {
            if (hasBettingWon) {
                wins++
                if (income != null) {
                    if (income > 0)
                        coefs.add(income + 1)
                    winPredicted++
                }
            } else {
                fails++
                if (income != null)
                    loseUnpredicted++
            }
            this.income += income.orDefault()
        }
    }

    fun execute(match: Match): PredictionPart

    fun updateFiles() {
        FileManager.putToFile(fileName, PredictionPart.gson.toJson(predictionStats))
    }
}