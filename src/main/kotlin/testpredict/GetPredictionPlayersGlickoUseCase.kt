package testpredict

import com.google.gson.reflect.TypeToken
import model.Match
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
        val radiantValue = Rating(
            mu = match.firstTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.firstTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.firstTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        val direValue = Rating(
            mu = match.secondTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.secondTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.secondTeamPlayers.sumOf { GlobalVars.players[it.id]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        val radiantMatches = match.firstTeamPlayers.sumOf { GlobalVars.players[it.id]?.matchCount ?: 0 } / 5
        val direMatches = match.secondTeamPlayers.sumOf { GlobalVars.players[it.id]?.matchCount ?: 0 } / 5


        val teamDiff = radiantValue.mu - direValue.mu
        val willRadiantWin = teamDiff > 0
        val absTeamDiff = if (radiantMatches > 10 && direMatches > 10) teamDiff.absoluteValue * 1.0 else 0.0

        return PredictionPart(willRadiantWin, absTeamDiff, PredictionPart.getWinRate(absTeamDiff, predictionStats, max, step))
    }

}