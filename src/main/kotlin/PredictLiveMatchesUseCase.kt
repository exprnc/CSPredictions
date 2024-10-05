import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.LiveMatch
import model.Player
import model.Team
import model.Tournament
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import utils.BASE_HLTV_URL
import utils.HLTV_USER_AGENT
import kotlin.random.Random

class PredictLiveMatchesUseCase {

    private val liveMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentLiveMatches, second value is pastLiveMatches
    private val upcomingMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentUpcomingMatches, second value is pastUpcomingMatches
    private var headers = mapOf<String, String>()

    fun execute() {
        runBlocking {
            headersSetup()
            getFinishedMatches()
            println("Receiving live matches has begun")
            while (true) {
                try {
                    matchesPage()
                    getPredict()
                    saveToJson()
                    reconfigureMatchSets()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@runBlocking
                }
            }
        }
    }

    private fun getFinishedMatches() {
        println("Receiving finished matches has begun")

    }

    private fun getPredict() {
        val matchesForPredict = upcomingMatches.first.filterNot { upcomingMatches.second.contains(it) }
        println("MATCHES FOR PREDICT:")
        matchesForPredict.forEach {
            println(it)
        }
        println()
    }

    private fun saveToJson() {
        val matchesForSave = liveMatches.second.filterNot { liveMatches.first.contains(it) }
        println("MATCHES FOR SAVE:")
        matchesForSave.forEach {
            println(it)
        }
        println()
    }

    private fun reconfigureMatchSets() {
        liveMatches.second.addAll(liveMatches.first)
        liveMatches.first.clear()
        upcomingMatches.second.addAll(upcomingMatches.first)
        upcomingMatches.first.clear()
    }

    private suspend fun matchesPage() {
        val matchesDoc = getDocument("$BASE_HLTV_URL/matches")
        val upcomingMatchesContainers = matchesDoc.select("div.upcomingMatch")
        val liveMatchesContainers = matchesDoc.select("div.liveMatch")
        for(container in upcomingMatchesContainers) {
            if(!isValidForPredict(container)) continue
            val upcomingMatchUrl = BASE_HLTV_URL + container.select("a.match.a-reset").attr("href")
            val upcomingMatchDoc = getDocument(upcomingMatchUrl)
            val tournament = getTournament(upcomingMatchDoc)
            if(tournament.id == 0L) continue
            matchPage(upcomingMatchDoc, upcomingMatchUrl, tournament, false)
        }
        for(container in liveMatchesContainers) {
            val liveMatchUrl = BASE_HLTV_URL + container.select("a.match.a-reset").attr("href")
            val liveMatchDoc = getDocument(liveMatchUrl)
            val tournament = getTournament(liveMatchDoc)
            if(tournament.id == 0L) continue
            matchPage(liveMatchDoc, liveMatchUrl, tournament, true)
        }
    }

    private fun isValidForPredict(matchContainer: Element): Boolean {
        if(matchContainer.attr("team1").isEmpty() || matchContainer.attr("team2").isEmpty()) return false
        val matchTimeUnix = matchContainer.select("div.matchTime").attr("data-unix").toLong()
        val currentTimeUnix = System.currentTimeMillis()
        return (matchTimeUnix - currentTimeUnix) < 3600000
    }

    private suspend fun getTournament(matchDoc: Document) : Tournament {
        val matchTournamentElement = matchDoc.select("div.event.text-ellipsis").select("a")
        val tournamentUrl = BASE_HLTV_URL + matchTournamentElement.attr("href")
        val tournamentId = extractSthFromUrl(tournamentUrl, "events/").toLong()
        val tournamentName = matchTournamentElement.attr("title")
        val tournamentType = getTournamentType(tournamentId)
        if(tournamentType == "Other") return Tournament(id = 0, name = "", type = "", prizePool = 0, isFinished = false)
        val tournamentPrizePool = getDocument(tournamentUrl).select("td.prizepool.text-ellipsis").text()

        return Tournament(
            id = tournamentId,
            name = tournamentName,
            type = tournamentType,
            prizePool = getPrizePoolValue(tournamentPrizePool),
            isFinished = false
        )
    }

    private suspend fun getTournamentType(tournamentId: Long): String {
        val tournamentsTypes = listOf("MAJOR", "INTLLAN", "REGIONALLAN", "ONLINE", "LOCALLAN")
        tournamentsTypes.forEach { type ->
            val ongoingTournamentsDoc = getDocument("$BASE_HLTV_URL/events?eventType=$type#tab-ALL", from = 200, until = 400)
            val allTournamentsTab = ongoingTournamentsDoc.select("div#ALL.tab-content")
            allTournamentsTab.select("a.a-reset.ongoing-event").forEach { tournamentElement ->
                val currentTournamentId = extractSthFromUrl(tournamentElement.attr("href"),"events/").toLong()
                if(currentTournamentId == tournamentId) {
                    return when(type) {
                        "MAJOR" -> "Major"
                        "INTLLAN" -> "Intl. LAN"
                        "REGIONALLAN" -> "Reg. LAN"
                        "ONLINE" -> "Online"
                        "LOCALLAN" -> "Local LAN"
                        else -> "Other"
                    }
                }
            }
        }
        return "Other"
    }

    private fun getPrizePoolValue(prizePool: String) : Int {
        if(prizePool.contains("$")) return prizePool.filter { it.isDigit() }.toInt()
        return 0
    }

    private fun matchPage(liveMatchDoc: Document, liveMatchUrl: String, tournament: Tournament, isLiveMatch: Boolean) {
        val matchId = extractSthFromUrl(liveMatchUrl, "matches/").toLong()
        val matchDescription = liveMatchDoc.select("div.padding.preformatted-text").text()
        val matchBestOf = getMatchBestOf(matchDescription)
        val teamsBoxElement = liveMatchDoc.select("div.standard-box.teamsBox")
        val firstTeamBoxElement = teamsBoxElement.select("div.team")[0]
        val secondTeamBoxElement = teamsBoxElement.select("div.team")[1]
        val firstTeamUrl = BASE_HLTV_URL + firstTeamBoxElement.select("a").attr("href")
        val secondTeamUrl = BASE_HLTV_URL + secondTeamBoxElement.select("a").attr("href")
        val firstTeamId = extractSthFromUrl(firstTeamUrl, "team/").toLong()
        val secondTeamId = extractSthFromUrl(secondTeamUrl, "team/").toLong()
        val firstTeamName = firstTeamBoxElement.select("div.teamName").text()
        val secondTeamName = secondTeamBoxElement.select("div.teamName").text()
        val firstTeamLineupElement = liveMatchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[0]
        val secondTeamLineupElement = liveMatchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[1]
        val firstTeamRanking = getTeamRanking(firstTeamLineupElement)
        val secondTeamRanking = getTeamRanking(secondTeamLineupElement)
        val firstTeamPlayers = getTeamPlayers(firstTeamLineupElement)
        val secondTeamPlayers = getTeamPlayers(secondTeamLineupElement)
        if(firstTeamPlayers.isEmpty() || secondTeamPlayers.isEmpty()) return

        if(isLiveMatch) {
            liveMatches.first.add(
                LiveMatch(
                    id = matchId,
                    url = liveMatchUrl,
                    bestOf = matchBestOf,
                    tournament = tournament,
                    firstTeam = Team(id = firstTeamId, name = firstTeamName),
                    secondTeam = Team(id = secondTeamId, name = secondTeamName),
                    firstTeamRanking = firstTeamRanking,
                    secondTeamRanking = secondTeamRanking,
                    firstTeamPlayers = firstTeamPlayers,
                    secondTeamPlayers = secondTeamPlayers
                )
            )
        } else {
            upcomingMatches.first.add(
                LiveMatch(
                    id = matchId,
                    url = liveMatchUrl,
                    bestOf = matchBestOf,
                    tournament = tournament,
                    firstTeam = Team(id = firstTeamId, name = firstTeamName),
                    secondTeam = Team(id = secondTeamId, name = secondTeamName),
                    firstTeamRanking = firstTeamRanking,
                    secondTeamRanking = secondTeamRanking,
                    firstTeamPlayers = firstTeamPlayers,
                    secondTeamPlayers = secondTeamPlayers
                )
            )
        }
    }

    private fun getTeamPlayers(lineupElement: Element): List<Player> {
        val players = mutableListOf<Player>()
        val playersElements = lineupElement.select("div.players").select("tr")[1].select("td.player")
        playersElements.forEach { playerElement ->
            val playerCompare = playerElement.select("div.player-compare")
            val dataPlayerId = playerCompare.attr("data-player-id")
            if(dataPlayerId.isEmpty()) return emptyList()
            val playerId = dataPlayerId.toLong()
            val playerName = playerCompare.select("div.text-ellipsis").text()
            players.add(
                Player(id = playerId, name = playerName)
            )
        }
        return players
    }

    private fun getTeamRanking(lineupElement: Element): Int {
        val teamRank = lineupElement.select("div.teamRanking").text().filter { it.isDigit() }
        return if(teamRank.isNotEmpty()) teamRank.toInt() else 0
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

    private suspend fun headersSetup() {
        while (true) {
            try {
                println("Setting up headers...")

                val connection = Jsoup.connect(BASE_HLTV_URL)
                connection.userAgent(HLTV_USER_AGENT)

                delay(Random.nextLong(3500, 5000))
                connection.get()

                headers = connection.response().headers()

                return
            } catch (e: Exception) {
                e.printStackTrace()
                println("Delaying...")
                delay(Random.nextLong(10000, 15000))
            }
        }
    }

    private suspend fun getDocument(url: String, from: Long = 1500, until: Long = 3000): Document {
        while (true) {
            try {
                println(url)
                val connection = Jsoup.connect(url)

                connection.userAgent(HLTV_USER_AGENT)
                connection.headers(headers)

                delay(Random.nextLong(from, until))
                val document = connection.get()

                headers = connection.response().headers()

                return document
            } catch (e: Exception) {
                e.printStackTrace()
                println("Delaying...")
                delay(Random.nextLong(5000, 10000))
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
}