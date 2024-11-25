package bot.predictor

import bot.GlobalVars
import com.google.gson.reflect.TypeToken
import model.Match
import model.PredictionPart
import model.Rating
import utils.FileManager
import java.lang.reflect.Type
import kotlin.math.absoluteValue

object GetPredictionPlayersGlickoUseCase : GetPredictionUseCase {

    override val step = 20
    override val max = 400
    override val fileName = "predictionPlayersGlicko"

    override val predictionStats: MutableMap<Int, PredictStats> = try {
        val listType: Type = object : TypeToken<MutableMap<Int, PredictStats>?>() {}.type
        PredictionPart.gson.fromJson<MutableMap<Int, PredictStats>?>(FileManager.readFromFile(fileName), listType) ?: mutableMapOf()
    } catch (e: Exception) {
        mutableMapOf()
    }

    override fun execute(match: Match): PredictionPart {
        val firstTeamValue = Rating(
            mu = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        val secondTeamValue = Rating(
            mu = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        val firstTeamMatches = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.matchCount ?: 0 } / 5
        val secondTeamMatches = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.matchCount ?: 0 } / 5


        val teamDiff = firstTeamValue.mu - secondTeamValue.mu
        val willFirstTeamWin = teamDiff > 0
        val absTeamDiff = if (firstTeamMatches > 10 && secondTeamMatches > 10) teamDiff.absoluteValue * 1.0 else 0.0

        return PredictionPart(willFirstTeamWin, absTeamDiff, PredictionPart.getWinRate(absTeamDiff, predictionStats, max, step))
    }
}