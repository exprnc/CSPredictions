package utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import model.PlayerStats
import model.PredictionInfo
import java.io.File

object JsonManager {

    private val gson = Gson()

    fun getAllMatches() : List<Match> {
        val json = File("matches.json").readText()
        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getPredictedMatches() : MutableMap<Long, PredictionInfo> {
        val json = File("predictedMatches.json").readText()
        val type = object : TypeToken<MutableMap<Long, PredictionInfo>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getAllPlayers() : List<PlayerStats> {
        val json = File("players.json").readText()
        val type = object : TypeToken<List<PlayerStats>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getAllMatchesWithCoef() : List<Match> {
        val json = File("eGamersMatchesWithCoefs.json").readText()
        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson<List<Match>?>(json, type)
    }
}