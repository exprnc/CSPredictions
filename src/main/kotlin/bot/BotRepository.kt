package bot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import utils.FileManager
import model.PlayerStats
import utils.sort

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
        GlobalVars.matches = gson.fromJson<List<Match>>(json, type).orEmpty().filter { it.getTier() == 1 && it.bestOf != 1 }.toMutableList()
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
}
