import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.Match
import model.Player
import model.Team
import model.Tournament
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import utils.BASE_HLTV_URL
import java.io.FileWriter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

class GetArchiveMatchesUseCase {

    private val archiveUrl = "/events/archive"
    private val tournamentPages = 25 // 50 tournaments on one page
    private val archiveMatches = mutableListOf<Match>()
    private var headers = mapOf<String, String>()

    fun execute() {
        runBlocking {
            try {
                println("Receiving archive matches has begun")
                var offset = 0
                for (page in 1..tournamentPages) {
                    tournamentsPage(offset)
                    offset += 50
                }
                saveToJson()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                println("Receiving archive matches has ended")
            }
        }
    }

    private fun saveToJson() {
        val json = Gson().toJson(archiveMatches)
        FileWriter("matches.json").use { it.write(json) }
    }

    private suspend fun tournamentsPage(offset: Int) {
        val tournamentsDoc = getDocument("$BASE_HLTV_URL$archiveUrl?offset=$offset")
        val tournamentsElements = tournamentsDoc.select("a.a-reset.small-event.standard-box")
        for (tournamentElement in tournamentsElements) {
            val tournamentType = getTournamentType(tournamentElement)
            if(tournamentType == "Other") continue
            tournamentPage(tournamentElement, tournamentType)
        }
    }

    private fun getTournamentType(tournament: Element) : String {
        val typeElement = tournament.select("td.col-value.small-col.gtSmartphone-only")
        return typeElement.text()
    }

    private suspend fun tournamentPage(tournamentElement: Element, tournamentType: String) {
        val tournamentUrl = BASE_HLTV_URL + tournamentElement.attr("href")
        val tournamentDoc = getDocument(tournamentUrl)
        val tournamentId = extractSthFromUrl(tournamentUrl, "events/").toLong()
        val tournamentName = tournamentDoc.select("h1.event-hub-title").text()
        val tournamentPrizePool = tournamentDoc.select("td.prizepool.text-ellipsis").text()
        val tournament = Tournament(
            id = tournamentId,
            name = tournamentName,
            type = tournamentType,
            prizePool = getPrizePoolValue(tournamentPrizePool),
            isFinished = true
        )

        tournamentResultsPage(tournament)
    }

    private suspend fun tournamentResultsPage(
        tournament: Tournament
    ) {
        val resultsDoc = getDocument("$BASE_HLTV_URL/results?event=${tournament.id}")
        val resultsElements = resultsDoc.select("div.results-all").select("a.a-reset")
        for(resultElement in resultsElements) {
            val matchUrl = BASE_HLTV_URL + resultElement.attr("href")
            val matchDoc = getDocument(matchUrl)
            if(matchWasCancelled(matchDoc)) continue
            matchPage(matchDoc, matchUrl, tournament)
        }
    }

    private suspend fun matchPage(
        matchDoc: Document,
        matchUrl: String,
        tournament: Tournament
    ) {
        val matchId = extractSthFromUrl(matchUrl, "matches/").toLong()
        val matchDescription = matchDoc.select("div.padding.preformatted-text").text()
        val matchBestOf = getMatchBestOf(matchDescription)
        val matchTeamsBoxElement = matchDoc.select("div.standard-box.teamsBox")
        val firstTeamBoxElement = matchTeamsBoxElement.select("div.team")[0]
        val secondTeamBoxElement = matchTeamsBoxElement.select("div.team")[1]
        val startDateUnix = matchTeamsBoxElement.select("div.time").attr("data-unix").toLong()
        val matchStartDate = convertUnixToString(startDateUnix)
        val firstTeamUrl = BASE_HLTV_URL + firstTeamBoxElement.select("a").attr("href")
        val secondTeamUrl = BASE_HLTV_URL + secondTeamBoxElement.select("a").attr("href")
        val matchFirstTeamId = extractSthFromUrl(firstTeamUrl, "team/").toLong()
        val matchSecondTeamId = extractSthFromUrl(secondTeamUrl, "team/").toLong()
        val matchFirstTeamName = firstTeamBoxElement.select("div.teamName").text()
        val matchSecondTeamName = secondTeamBoxElement.select("div.teamName").text()
        val matchFirstTeamRanking = getTeamRanking(matchFirstTeamId, matchStartDate)
        val matchSecondTeamRanking = getTeamRanking(matchSecondTeamId, matchStartDate)
        val matchFirstTeamScore = firstTeamBoxElement.select("div.team1-gradient").select("div.lost, div.won").text().toInt()
        val matchSecondTeamScore = secondTeamBoxElement.select("div.team2-gradient").select("div.lost, div.won").text().toInt()
        val hasFirstTeamWon = matchFirstTeamScore > matchSecondTeamScore
        val statsElement = matchDoc.select("div#all-content.stats-content")
        val firstTeamPlayersStatsElement = statsElement.select("table.table.totalstats")[0]
        val secondTeamPlayersStatsElement = statsElement.select("table.table.totalstats")[1]
        val firstTeamPlayers = getTeamPlayers(firstTeamPlayersStatsElement)
        val secondTeamPlayers = getTeamPlayers(secondTeamPlayersStatsElement)

        val match = Match(
            id = matchId,
            bestOf = matchBestOf,
            tournament = tournament,
            firstTeam = Team(id = matchFirstTeamId, name = matchFirstTeamName),
            secondTeam = Team(id = matchSecondTeamId, name = matchSecondTeamName),
            firstTeamRanking = matchFirstTeamRanking,
            secondTeamRanking = matchSecondTeamRanking,
            firstTeamScore = matchFirstTeamScore,
            secondTeamScore = matchSecondTeamScore,
            hasFirstTeamWon = hasFirstTeamWon,
            firstTeamPlayers = firstTeamPlayers,
            secondTeamPlayers = secondTeamPlayers
        )

        archiveMatches.add(match)
    }

    private fun getTeamPlayers(playersStatsElement: Element): List<Player> {
        val players = mutableListOf<Player>()
        for (playerStatsElement in playersStatsElement.select("tbody").select("tr")) {
            if(playerStatsElement.className() == "header-row") continue
            if(playerStatsElement.select("td.adr.text-center").text() == "-") continue
            val playerLinkElement = playerStatsElement.select("td.players").select("a")
            val playerId = extractSthFromUrl(playerLinkElement.attr("href"), "player/").toLong()
            val playerName = playerLinkElement.select("div.smartphone-only.statsPlayerName").text()
            val player = Player(id = playerId, name = playerName)
            players.add(player)
        }
        return players
    }

    private fun getPrizePoolValue(prizePool: String) : Int {
        if(prizePool.contains("$")) return prizePool.filter { it.isDigit() }.toInt()
        return 0
    }

    private fun convertUnixToString(unixDate: Long): String {
        val instant = Instant.ofEpochMilli(unixDate)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneOffset.UTC)
        return formatter.format(instant)
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
                val connection: Connection = if(url == "https://www.hltv.org/ranking/teams/2024/march/4") {
                    println("https://www.hltv.org/ranking/teams/2024/march/5")
                    Jsoup.connect("https://www.hltv.org/ranking/teams/2024/march/5")
                } else {
                    println(url)
                    Jsoup.connect(url)
                }

                connection.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0")
                connection.headers(headers)

                delay(Random.nextLong(100, 200))
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