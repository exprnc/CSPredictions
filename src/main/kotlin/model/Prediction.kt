package model

data class Prediction(
    val willFirstTeamWin: Boolean?,
    val minCoef: Double?,
    val team: PredictionPart?,
    val teamGlicko: PredictionPart?,
    val bestPrediction: PredictionPart?,
    val type: PredictionType?
)