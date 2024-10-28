package utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import java.io.File

object JsonManager {

    private val gson = Gson()

    fun getAllMatches() : List<Match> {
        val json = File("matches.json").readText()
        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getAllMatchesWithCoef() : List<Match> {
        val json = File("matchesWithCoefs.json").readText()
        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson<List<Match>?>(json, type).filter { it.bestOf == 3 }
    }
}