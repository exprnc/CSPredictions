package bot

import model.Match
import model.PlayerStats
import model.Team
import model.Tournament

object GlobalVars {
    var players = mutableMapOf<Long, PlayerStats>()
    var tournaments = mutableMapOf<Long, Tournament>()
    var teams = mutableMapOf<Long, Team>()
    var matches = mutableListOf<Match>()
}