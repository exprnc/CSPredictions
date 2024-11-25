package model

data class PredictionInfo(
    val messageId: Long?,
    val match: Match,
    val prediction: Prediction?,
//        var bet: IncomeHandlerV2.IncomeMatch,
)