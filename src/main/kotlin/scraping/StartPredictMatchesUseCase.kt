package scraping

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import utils.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

class StartPredictMatchesUseCase {

    private val gson = Gson()

    private val predictedMatchesIds = mutableMapOf<Long, Long>() // first value is matchId, second value is messageId
    private val finishedMatches = mutableListOf<Match>()
    private val missedMatches = mutableListOf<Match>()
    private val liveMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentLiveMatches, second value is pastLiveMatches
    private val upcomingMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentUpcomingMatches, second value is pastUpcomingMatches
    private var headers = mapOf<String, String>()

    fun execute() {
        runBlocking {
            val bot = bot {
                token = TELEGRAM_BOT_TOKEN
                dispatch {
                    command("start") {
                        headersSetup()
                        getMissedMatches()
                        println("Predicting matches has begun")
                        while (true) {
                            try {
                                matchesPage()
                                getPredict(bot)
                                matchesForSave(bot)
                                reconfigureMatchSets()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                break
                            }
                        }
                    }
                }
            }
            bot.startPolling()
        }
    }

    private fun getPredict(bot: Bot) {
        val matchesForPredict = upcomingMatches.first.filterNot { upcomingMatches.second.contains(it) }
        println("MATCHES FOR PREDICT:")
        matchesForPredict.forEach {
            println(it)
        }
        matchesForPredict.forEach { match ->
            bot.sendMessage(
                chatId = ChatId.fromId(TEST_CHANNEL_ID),
                text = predictMessageForm(match)
            ).onSuccess {
                predictedMatchesIds[match.matchId] = it.messageId
            }
        }
        println()
    }

    private suspend fun matchesForSave(bot: Bot) {
        val matchesForSave = liveMatches.second.filterNot { liveMatches.first.contains(it) }
        println("MATCHES FOR SAVE:")
        matchesForSave.forEach {
            println(it)
        }
        matchesForSave.forEach { match ->
            getMatchForSave(match)
        }
        finishedMatches.forEach { finishedMatch ->
            val isSuccess = bot.editMessageText(
                chatId = ChatId.fromId(TEST_CHANNEL_ID),
                messageId = predictedMatchesIds[finishedMatch.matchId],
                text = editMessageForm(finishedMatch)
            ).first?.isSuccessful

            if(isSuccess == true) {
                predictedMatchesIds.remove(finishedMatch.matchId)
            }
        }
        println()
    }

    private fun predictMessageForm(match: LiveMatch): String {
        return "${match.tournament.name}/n/n"
    }

    private fun editMessageForm(match: Match): String {
        return ""
    }

    private suspend fun getMatchForSave(match: LiveMatch) {
        val matchDoc = getDocument(match.matchUrl)
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

        val finishedMatch = Match(
            matchId = extractSthFromUrl(match.matchUrl, "matches/").toLong(),
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

        finishedMatches.add(finishedMatch)
    }

    private fun reconfigureMatchSets() {
        liveMatches.second.addAll(liveMatches.first)
        liveMatches.first.clear()
        upcomingMatches.second.addAll(upcomingMatches.first)
        upcomingMatches.first.clear()
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

    private suspend fun getMissedMatches() {
        try {
            println("Receiving missed matches has begun")
            val currentMatches = JsonManager.getAllMatches()
            val lastMatch = currentMatches.first()
            var offset = 0
            while (true) {
                val resultsDoc = getDocument("$BASE_HLTV_URL/results?offset=$offset", from = 200, until = 400)
                val matchesElements = resultsDoc.select("div.results-holder.allres").select("div.result-con").select("a.a-reset")
                for (matchElement in matchesElements) {
                    val matchId = extractSthFromUrl(matchElement.attr("href"), "matches/").toLong()
                    if(matchId == lastMatch.matchId) {
                        saveMissedMatches(currentMatches.toMutableList())
                        return
                    }
                    missedMatchPage(matchElement)
                }
                offset += 100
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
    }

    private fun saveMissedMatches(currentMatches: MutableList<Match>) {
        currentMatches.addAll(0, missedMatches)
        File("matches.json").writeText(gson.toJson(currentMatches))
    }

    private suspend fun missedMatchPage(matchElement: Element) {
        val matchUrl = BASE_HLTV_URL + matchElement.attr("href")
        val matchDoc = getDocument(matchUrl, from = 200, until = 400)
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

        val missedMatch = Match(
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

        missedMatches.add(missedMatch)
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

    private suspend fun matchesPage() {
        val matchesDoc = getDocument("$BASE_HLTV_URL/matches")
        val upcomingMatchesContainers = matchesDoc.select("div.upcomingMatch")
        val liveMatchesContainers = matchesDoc.select("div.liveMatch")
        for(container in upcomingMatchesContainers) {
            if(!isValidForPredict(container)) continue
            val upcomingMatchUrl = BASE_HLTV_URL + container.select("a.match.a-reset").attr("href")
            val upcomingMatchDoc = getDocument(upcomingMatchUrl)
            val tournament = getTournament(upcomingMatchDoc)
            if(tournament.tournamentId == 0L) continue
            matchPage(upcomingMatchDoc, upcomingMatchUrl, tournament, false)
        }
        for(container in liveMatchesContainers) {
            val liveMatchUrl = BASE_HLTV_URL + container.select("a.match.a-reset").attr("href")
            val liveMatchDoc = getDocument(liveMatchUrl)
            val tournament = getTournament(liveMatchDoc)
            if(tournament.tournamentId == 0L) continue
            matchPage(liveMatchDoc, liveMatchUrl, tournament, true)
        }
    }

    private fun isValidForPredict(matchContainer: Element): Boolean {
        if(matchContainer.attr("team1").isEmpty() || matchContainer.attr("team2").isEmpty()) return false
        val matchTimeUnix = matchContainer.select("div.matchTime").attr("data-unix").toLong()
        val currentTimeUnix = System.currentTimeMillis()
        val validMillisForPredict: Long = 30 * 60000 // The first number indicates how many minutes should remain before the start of the match for the prediction
        return (matchTimeUnix - currentTimeUnix) < validMillisForPredict
    }

    private fun getTournament(matchDoc: Document) : Tournament {
        val matchTournamentElement = matchDoc.select("div.event.text-ellipsis").select("a")
        val tournamentUrl = BASE_HLTV_URL + matchTournamentElement.attr("href")
        val tournamentId = extractSthFromUrl(tournamentUrl, "events/").toLong()
        val tournamentName = matchTournamentElement.attr("title")

        return Tournament(
            tournamentId = tournamentId,
            name = tournamentName,
        )
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
                    matchId = matchId,
                    matchUrl = liveMatchUrl,
                    bestOf = matchBestOf,
                    tournament = tournament,
                    firstTeam = Team(teamId = firstTeamId, name = firstTeamName),
                    secondTeam = Team(teamId = secondTeamId, name = secondTeamName),
                    firstTeamRanking = firstTeamRanking,
                    secondTeamRanking = secondTeamRanking,
                    firstTeamPlayers = firstTeamPlayers,
                    secondTeamPlayers = secondTeamPlayers
                )
            )
        } else {
            upcomingMatches.first.add(
                LiveMatch(
                    matchId = matchId,
                    matchUrl = liveMatchUrl,
                    bestOf = matchBestOf,
                    tournament = tournament,
                    firstTeam = Team(teamId = firstTeamId, name = firstTeamName),
                    secondTeam = Team(teamId = secondTeamId, name = secondTeamName),
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
                Player(playerId = playerId, name = playerName)
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