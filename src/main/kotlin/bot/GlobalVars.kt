package bot

import model.*

object GlobalVars {
    var players = mutableMapOf<Long, PlayerStats>()
    var tournaments = mutableMapOf<Long, Tournament>()
    var teams = mutableMapOf<Long, Team>()
    var matches = mutableListOf<Match>()
    var predictedMatches = mutableMapOf<Long, PredictionInfo>()
}