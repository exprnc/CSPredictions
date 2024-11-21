package bot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import utils.FileManager
import model.PlayerStats

class BotRepository() {
    private val gson = Gson()

    fun getAllPlayers() {
        val json = FileManager.readFromFile("players")
        val type = object : TypeToken<List<PlayerStats>?>() {}.type
        GlobalVars.players = gson.fromJson<List<PlayerStats>?>(json, type).orEmpty().associateBy { it.playerId }.toMutableMap()
    }

    fun getAllMatches() {
        val json = FileManager.readFromFile("matches")
        val type = object : TypeToken<List<Match>>() {}.type
        GlobalVars.matches = gson.fromJson<List<Match>>(json, type).orEmpty().reversed().toMutableList()
    }

    fun updatePlayers() {
        val json = gson.toJson(GlobalVars.players.values.toList())
        FileManager.putToFile("players", json)
    }

    fun updateMatches(matches: List<Match>) {
        GlobalVars.matches.addAll(0, matches)
        val json = gson.toJson(GlobalVars.matches)
        FileManager.putToFile("matches", json)
    }
}
