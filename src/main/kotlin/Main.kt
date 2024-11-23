import model.Match
import model.Team
import model.Tournament
import scraping.StartPredictMatchesUseCase
import scraping.StartPredictMatchesUseCase.PredictionInfo

fun main() {
    StartPredictMatchesUseCase().execute()
}