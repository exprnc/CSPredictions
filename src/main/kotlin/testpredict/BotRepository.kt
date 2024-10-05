package testpredict

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match

class BotRepository() {
    private val gson = Gson()

    fun getAllPlayers() {
        val json = FileManager.readFromFile("players")
        val type = object : TypeToken<List<MemberInfo>?>() {}.type
        GlobalVars.players = gson.fromJson<List<MemberInfo>?>(json, type).orEmpty().associateBy { it.playerId }.toMutableMap()
    }

    fun getAllMatches() {
        val json = FileManager.readFromFile("matches")
        val type = object : TypeToken<List<Match>>() {}.type
        GlobalVars.matches = gson.fromJson<List<Match>>(json, type).orEmpty().sort().toMutableList()
    }

    private fun updatePlayers() {
        val json = gson.toJson(GlobalVars.players.values.toList())
        FileManager.putToFile("players", json)
    }

    fun updateMatches(matches: List<Match>) {
        GlobalVars.matches.addAll(matches)
        val json = gson.toJson(GlobalVars.matches.sort())
        FileManager.putToFile("matches", json)
    }

    private fun List<Match>.sort(): List<Match> {
        return this.sortedWith(compareByDescending<Match> { it.id }.thenByDescending { it.tournament.id })
    }
}
