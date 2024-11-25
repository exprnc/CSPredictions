package bot.predictor

import bot.GlobalVars
import com.google.gson.reflect.TypeToken
import model.Match
import model.PredictionPart
import utils.FileManager
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
        val firstTeamValue = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.rating ?: 1400 } / 5
        val secondTeamValue = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.rating ?: 1400 } / 5
        val teamDiff = firstTeamValue - secondTeamValue
        val willFirstTeamWin = teamDiff > 0
        val absTeamDiff = teamDiff.absoluteValue * 1.0

        return PredictionPart(willFirstTeamWin, absTeamDiff, PredictionPart.getWinRate(absTeamDiff, predictionStats, max, step))
    }
}