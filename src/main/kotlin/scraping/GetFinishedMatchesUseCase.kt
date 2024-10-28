package scraping

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.Match
import model.Player
import model.Team
import model.Tournament
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import utils.BASE_HLTV_URL
import utils.HLTV_USER_AGENT
import java.io.FileWriter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

class GetFinishedMatchesUseCase {

    private val resultsPages = 200 // 100 matches on one page
    private val finishedMatches = mutableListOf<Match>()
    private var headers = mapOf<String, String>()

    fun execute() {
        runBlocking {
            try {
                println("Receiving finished matches has begun")
                var offset = 0
                for (page in 1..resultsPages) {
                    resultsPage(offset)
                    offset += 100
                }
                saveToJson()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                println("Receiving finished matches has ended")
            }
        }
    }

    private fun saveToJson() {
        val json = Gson().toJson(finishedMatches)
        FileWriter("matches.json").use { it.write(json) }
    }

    private suspend fun resultsPage(offset: Int) {
        val resultsDoc = getDocument("$BASE_HLTV_URL/results?offset=$offset")
        val matchesElements = resultsDoc.select("div.results-holder.allres").select("div.result-con").select("a.a-reset")
        for (matchElement in matchesElements) {
            println(offset)
            matchPage(matchElement)
        }
    }

    private suspend fun matchPage(matchElement: Element) {
        val matchUrl = BASE_HLTV_URL + matchElement.attr("href")
        val matchDoc = getDocument(matchUrl)
        if(matchWasCancelled(matchDoc)) return
        val tournamentInfoElement = matchDoc.select("div.event.text-ellipsis").select("a")
        val description = matchDoc.select("div.padding.preformatted-text").text()
        val teamsBoxElement = matchDoc.select("div.standard-box.teamsBox")
        val firstTeamBoxElement = teamsBoxElement.select("div.team")[0]
        val secondTeamBoxElement = teamsBoxElement.select("div.team")[1]
        val firstTeamUrl = BASE_HLTV_URL + firstTeamBoxElement.select("a").attr("href")
        val secondTeamUrl = BASE_HLTV_URL + secondTeamBoxElement.select("a").attr("href")
        val firstTeamId = extractSthFromUrl(firstTeamUrl, "team/").toLong()
        val secondTeamId = extractSthFromUrl(secondTeamUrl, "team/").toLong()
        val firstTeamName = firstTeamBoxElement.select("div.teamName").text()
        val secondTeamName = secondTeamBoxElement.select("div.teamName").text()
        val firstTeamScore = firstTeamBoxElement.select("div.team1-gradient").select("div.lost, div.won, div.tie").text().toInt()
        val secondTeamScore = secondTeamBoxElement.select("div.team2-gradient").select("div.lost, div.won, div.tie").text().toInt()
        if(firstTeamScore == secondTeamScore) return

        var firstTeamRanking: Int
        var secondTeamRanking: Int
        var firstTeamPlayers: List<Player>
        var secondTeamPlayers: List<Player>

        try {
            val firstTeamLineupElement = matchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[0]
            val secondTeamLineupElement = matchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[1]
            firstTeamRanking = getTeamRanking(firstTeamLineupElement)
            secondTeamRanking = getTeamRanking(secondTeamLineupElement)
            firstTeamPlayers = getTeamPlayersWithLineupElement(firstTeamLineupElement)
            secondTeamPlayers = getTeamPlayersWithLineupElement(secondTeamLineupElement)
            if(firstTeamPlayers.isEmpty() || secondTeamPlayers.isEmpty()) return
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
            val startDateUnix = teamsBoxElement.select("div.time").attr("data-unix").toLong()
            val matchStartDate = convertUnixToString(startDateUnix)
            firstTeamRanking = getTeamRanking(firstTeamId, matchStartDate)
            secondTeamRanking = getTeamRanking(secondTeamId, matchStartDate)
            val statsElement = matchDoc.select("div#all-content.stats-content")
            val firstTeamPlayersStatsElement = statsElement.select("table.table.totalstats")[0]
            val secondTeamPlayersStatsElement = statsElement.select("table.table.totalstats")[1]
            firstTeamPlayers = getTeamPlayersWithStatsElement(firstTeamPlayersStatsElement)
            secondTeamPlayers = getTeamPlayersWithStatsElement(secondTeamPlayersStatsElement)
        }

        val match = Match(
            matchId = extractSthFromUrl(matchUrl, "matches/").toLong(),
            bestOf = getMatchBestOf(description),
            tournament = Tournament(
                tournamentId = extractSthFromUrl(tournamentInfoElement.attr("href"), "events/").toLong(),
                name = tournamentInfoElement.attr("title")
            ),
            firstTeam = Team(teamId = firstTeamId, name = firstTeamName),
            secondTeam = Team(teamId = secondTeamId, name = secondTeamName),
            firstTeamRanking = firstTeamRanking,
            secondTeamRanking = secondTeamRanking,
            firstTeamScore = firstTeamScore,
            secondTeamScore = secondTeamScore,
            hasFirstTeamWon = firstTeamScore > secondTeamScore,
            firstTeamPlayers = firstTeamPlayers,
            secondTeamPlayers = secondTeamPlayers
        )

        println(match)

        finishedMatches.add(match)
    }

    private fun matchWasCancelled(matchDoc: Document): Boolean {
        val singleMatchesStatsElements = matchDoc.select("div.results.played")
        singleMatchesStatsElements.forEach { singleMatchStatsElement ->
            val parentStatsElement = singleMatchStatsElement.parent()
            val mapName = parentStatsElement!!.select("div.mapname").text()
            val resultsStats = singleMatchStatsElement.selectFirst("a.results-stats")
            if(mapName.contains("Default") || resultsStats == null) return true
        }
        return false
    }

    private fun getTeamPlayersWithLineupElement(lineupElement: Element): List<Player> {
        val players = mutableListOf<Player>()
        val playersElements = lineupElement.select("div.players").select("tr")[1].select("td.player")
        playersElements.forEach { playerElement ->
            val dataPlayerId = playerElement.select("div.flagAlign").attr("data-player-id")
            if(dataPlayerId.isEmpty()) return emptyList()
            val playerId = dataPlayerId.toLong()
            val playerName = playerElement.select("div.flagAlign").select("div.text-ellipsis").text()
            players.add(
                Player(playerId = playerId, name = playerName)
            )
        }
        return players
    }

    private fun convertUnixToString(unixDate: Long): String {
        val instant = Instant.ofEpochMilli(unixDate)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneOffset.UTC)
        return formatter.format(instant)
    }

    private fun getTeamRanking(lineupElement: Element): Int {
        val teamRank = lineupElement.select("div.teamRanking").text().filter { it.isDigit() }
        return if(teamRank.isNotEmpty()) teamRank.toInt() else 0
    }

    private fun getTeamPlayersWithStatsElement(statsElement: Element): List<Player> {
        val players = mutableListOf<Player>()
        for (playerStatsElement in statsElement.select("tbody").select("tr")) {
            if(playerStatsElement.className() == "header-row") continue
            if(playerStatsElement.select("td.adr.text-center").text() == "-") continue
            val playerLinkElement = playerStatsElement.select("td.players").select("a")
            val playerId = extractSthFromUrl(playerLinkElement.attr("href"), "player/").toLong()
            val playerName = playerLinkElement.select("div.smartphone-only.statsPlayerName").text()
            val player = Player(playerId = playerId, name = playerName)
            players.add(player)
        }
        return players
    }

    private suspend fun getTeamRanking(teamId: Long, matchStartDate: String): Int {
        val worldRankingTeamsIdsByDate = getWorldRankingTeamsIdsByDate(matchStartDate)
        val index = worldRankingTeamsIdsByDate.indexOf(teamId)
        return if (index != -1) (index + 1) else 0
    }

    private suspend fun getWorldRankingTeamsIdsByDate(date: String): List<Long> {
        val worldRankingTeamsIds = mutableListOf<Long>()
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val dateTime = LocalDateTime.parse(date, formatter)
        val localDate = dateTime.toLocalDate()
        val mondayOfWeek = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val year = mondayOfWeek.year
        val day = mondayOfWeek.dayOfMonth
        val month = mondayOfWeek.month.name.lowercase()
        val rankingDoc = getDocument("$BASE_HLTV_URL/ranking/teams/$year/$month/$day")
        val rankedTeamsElements = rankingDoc.select("div.ranking").select("div.ranked-team.standard-box")
        rankedTeamsElements.forEach { rankedTeam ->
            val teamUrl = BASE_HLTV_URL + rankedTeam.select("a.moreLink").attr("href")
            val teamId = extractSthFromUrl(teamUrl, "team/").toLong()
            worldRankingTeamsIds.add(teamId)
        }
        return worldRankingTeamsIds
    }

    private fun getMatchBestOf(description: String): Int {
        return description.run {
            when {
                contains("Best of 1") -> 1
                contains("Best of 2") -> 2
                contains("Best of 3") -> 3
                contains("Best of 4") -> 4
                contains("Best of 5") -> 5
                contains("Best of 6") -> 6
                contains("Best of 7") -> 7
                contains("Best of 8") -> 8
                contains("Best of 9") -> 9
                contains("Best of 10") -> 10
                else -> 0
            }
        }
    }

    private fun extractSthFromUrl(url: String, after: String, before: String = "/"): String {
        val startIndex = url.indexOf(after) + after.length
        if (startIndex == after.length - 1) return ""
        val endIndex = url.indexOf(before, startIndex)
        return if (endIndex != -1) {
            url.substring(startIndex, endIndex)
        } else {
            url.substring(startIndex)
        }
    }

    private suspend fun getDocument(url: String): Document {
        while (true) {
            try {
                println(url)
                val connection = Jsoup.connect(url)

                connection.userAgent(HLTV_USER_AGENT)
                connection.headers(headers)

                val document: Document = connection.get()

                headers = connection.response().headers()

                return document
            } catch (e: Exception) {
                e.printStackTrace()
                println("Delaying...")
                delay(Random.nextLong(5000, 10000))
            }
        }
    }
}