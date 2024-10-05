package testpredict

import com.google.gson.reflect.TypeToken
import model.Match
import java.lang.reflect.Type
import kotlin.math.absoluteValue

object GetPredictionPlayersUseCase : GetPredictionUseCase {
    override val step = 30
    override val max = 240
    override val fileName = "predictionPlayersElo"
    override val predictionStats: MutableMap<Int, PredictStats> = try {
        val listType: Type = object : TypeToken<MutableMap<Int, PredictStats>?>() {}.type
        PredictionPart.gson.fromJson<MutableMap<Int, PredictStats>?>(FileManager.readFromFile(fileName), listType) ?: mutableMapOf()
    } catch (e: Exception) {
        mutableMapOf()
    }

    override fun execute(match: Match): PredictionPart {
        val radiantValue = match.firstTeamPlayers.sumOf { GlobalVars.players[it.id]?.rating ?: 1400 } / 5
        val direValue = match.secondTeamPlayers.sumOf { GlobalVars.players[it.id]?.rating ?: 1400 } / 5
        val teamDiff = radiantValue - direValue
        val willRadiantWin = teamDiff > 0
        val absTeamDiff = teamDiff.absoluteValue * 1.0

        return PredictionPart(willRadiantWin, absTeamDiff, PredictionPart.getWinRate(absTeamDiff, predictionStats, max, step))
    }
}