package testpredict

import model.Match
import model.Team
import model.Tournament

object GlobalVars {
    var players = mutableMapOf<Long, MemberInfo>()
    var tournaments = mutableMapOf<Long, Tournament>()
    var teams = mutableMapOf<Long, Team>()
    var matches = mutableListOf<Match>()

    fun getTier(match: Match): Int {
        //TODO
        return 1
    }
}