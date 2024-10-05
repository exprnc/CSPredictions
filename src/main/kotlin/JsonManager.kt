import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import model.Match
import java.io.File

object JsonManager {

    private val gson = Gson()

    fun putMatches(matches: List<Match>) {
        val currentMatches = getAllMatches().toMutableList()
        currentMatches.addAll(matches)
        updateMatches(matches)
    }

    fun putMatch(match: Match) {
        val matches = getAllMatches().toMutableList()
        matches.add(match)
        updateMatches(matches)
    }

    fun getMatchById(matchId: Long): Match {
        return getAllMatches().first { it.id == matchId }
    }

    fun updateMatch(match: Match) {
        val matches = getAllMatches().toMutableList()
        val index = matches.indexOfFirst { it.id == match.id }
        if (index != -1) {
            matches[index] = match
            updateMatches(matches)
        }
    }

    fun deleteMatchById(matchId: Long) {
        val matches = getAllMatches().toMutableList()
        matches.removeIf { it.id == matchId }
        updateMatches(matches)
    }

    private fun getAllMatches(): List<Match> {
        val json = File("matches.json").readText()
        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun updateMatches(matches: List<Match>) {
        File("matches.json").writeText(gson.toJson(matches.sort()))
    }

    private fun List<Match>.sort(): List<Match> {
        return this.sortedWith(compareByDescending<Match> { it.id }.thenByDescending { it.tournament.id })
    }
}