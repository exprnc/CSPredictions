package scraping

import bot.BotRepository
import bot.GlobalVars
import bot.calibrator.Calibrator
import bot.predictor.GetPredictionPlayersGlickoUseCase
import bot.predictor.GetPredictionPlayersUseCase
import bot.predictor.Predictor
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private var isFirstLaunch = true

    private val gson = Gson()

    private val botRepository = BotRepository()
    private val predictor = Predictor()
    private val calibrator = Calibrator()

    private val finishedMatches = mutableListOf<Match>()
    private val missedMatches = mutableListOf<Match>()
    private val liveMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentLiveMatches, second value is pastLiveMatches
    private val upcomingMatches = Pair(mutableSetOf<LiveMatch>(), mutableSetOf<LiveMatch>()) // first value is currentUpcomingMatches, second value is pastUpcomingMatches
    private var headers = mapOf<String, String>()

    fun execute() {
        runBlocking {
            try {
                val bot = bot {
                    token = TELEGRAM_BOT_TOKEN
                    dispatch {
                        command("start") {
                            headersSetup()
                            loadData()
                            getMissedMatches()
                            println("Predicting matches has begun")
                            while (true) {
                                matchesPage()
                                getPredict(bot)
                                matchesForSave(bot)
                                reconfigureMatchSets()
                            }
                        }
                    }
                }
                bot.startPolling()
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }

    private fun loadData() {
        println("Loading data...")
        GlobalVars.predictedMatches = JsonManager.getPredictedMatches()
        botRepository.getAllPlayers()
        val matches = JsonManager.getAllMatches().toSet().toList()
        GlobalVars.matches = matches.toMutableList()
    }

    private fun LiveMatch.toMatch() : Match {
        return Match(
            matchId = this.matchId,
            bestOf = this.bestOf,
            tournament = this.tournament,
            firstTeam = this.firstTeam,
            secondTeam = this.secondTeam,
            firstTeamRanking = this.firstTeamRanking,
            secondTeamRanking = this.secondTeamRanking,
            firstTeamScore = 0,
            secondTeamScore = 0,
            hasFirstTeamWon = false,
            firstTeamPlayers = this.firstTeamPlayers,
            secondTeamPlayers = this.secondTeamPlayers
        )
    }

    private fun getPredict(bot: Bot) {
        val matchesForPredict = upcomingMatches.first.filterNot { upcomingMatches.second.contains(it) }.toMutableSet()

        if(isFirstLaunch) {
            liveMatches.first.forEach {
                matchesForPredict.add(it)
            }
            isFirstLaunch = false
        }
        println("MATCHES FOR PREDICT:")
        matchesForPredict.forEach {
            println("${it.matchId}: ${it.firstTeam.name} VS ${it.secondTeam.name} ${it.tournament.name}: $it")
        }

        for(liveMatch in matchesForPredict) {
            if(GlobalVars.predictedMatches.containsKey(liveMatch.matchId)) {
                println("predictedMatches.containsKey(liveMatch.matchId)")
                continue
            }
            val prediction = predictor.getPredictionByMatch(liveMatch.toMatch())
//            val bet = IncomeHandlerV2.IncomeMatch.createFromOdds(liveMatch.toMatch(), prediction)
            bot.sendMessage(
                chatId = ChatId.fromId(TEST_CHANNEL_ID),
                text = predictMessageForm(liveMatch, prediction)
            ).onSuccess {
                GlobalVars.predictedMatches[liveMatch.matchId] = PredictionInfo(messageId = it.messageId, liveMatch.toMatch(), prediction)
                println("PREDICT SEND MESSAGE SUCCESS")
            }.onError {
                GlobalVars.predictedMatches[liveMatch.matchId] = PredictionInfo(messageId = null, liveMatch.toMatch(), prediction)
                println("PREDICT SEND MESSAGE ERROR")
            }
        }
        File("predictedMatches.json").writeText(gson.toJson(GlobalVars.predictedMatches))
    }

    private fun updateBotStats(bot: Bot, match: Match, prediction: Prediction) {
        val oldBotStats = getBotStats()

        val newBotStats: BotStats = if(match.hasFirstTeamWon == prediction.willFirstTeamWin) {

            val totalMatches = oldBotStats.totalMatches + 1
            val wins = oldBotStats.wins + 1
            val fails = oldBotStats.fails
            val winRate = if(wins + fails == 0) {
                0.0
            } else {
                (wins * 1.0 / (wins + fails) * 100).round2()
            }
            val predictedCount = wins + fails
            val unpredictedCount = totalMatches - predictedCount
            val predicted = if(predictedCount + unpredictedCount == 0) {
                0.0
            } else {
                (predictedCount * 1.0 / (predictedCount + unpredictedCount) * 100).round2()
            }

            BotStats(
                totalMatches = totalMatches,
                wins = wins,
                fails = fails,
                winRate = winRate,
                predicted = predicted
            )
        } else if(prediction.willFirstTeamWin == null) {

            val totalMatches = oldBotStats.totalMatches + 1
            val wins = oldBotStats.wins
            val fails = oldBotStats.fails
            val winRate = if(wins + fails == 0) {
                0.0
            } else {
                (wins * 1.0 / (wins + fails) * 100).round2()
            }
            val predictedCount = wins + fails
            val unpredictedCount = totalMatches - predictedCount
            val predicted = if(predictedCount + unpredictedCount == 0) {
                0.0
            } else {
                (predictedCount * 1.0 / (predictedCount + unpredictedCount) * 100).round2()
            }

            BotStats(
                totalMatches = totalMatches,
                wins = wins,
                fails = fails,
                winRate = winRate,
                predicted = predicted
            )
        } else {

            val totalMatches = oldBotStats.totalMatches + 1
            val wins = oldBotStats.wins
            val fails = oldBotStats.fails + 1
            val winRate = if(wins + fails == 0) {
                0.0
            } else {
                (wins * 1.0 / (wins + fails) * 100).round2()
            }
            val predictedCount = wins + fails
            val unpredictedCount = totalMatches - predictedCount
            val predicted = if(predictedCount + unpredictedCount == 0) {
                0.0
            } else {
                (predictedCount * 1.0 / (predictedCount + unpredictedCount) * 100).round2()
            }

            BotStats(
                totalMatches = totalMatches,
                wins = wins,
                fails = fails,
                winRate = winRate,
                predicted = predicted
            )
        }

        println("NEW BOT STATS: $newBotStats")

        val editBotStatsPair = bot.editMessageText(
            chatId = ChatId.fromId(TEST_CHANNEL_ID),
            messageId = BOT_STATS_MESSAGE_ID,
            text = botStatsMessageForm(botStats = newBotStats)
        )

        when (editBotStatsPair.first?.isSuccessful) {
            true -> {
                println("editBotStatsPair.first?.isSuccessful == true")
            }
            false -> {
                println("editBotStatsPair.first?.isSuccessful == false")
            }
            else -> {
                println("editBotStatsPair.first?.isSuccessful == null")
            }
        }

        if(editBotStatsPair.second == null) {
            println("editBotStatsPair.second == null")
        } else {
            println("editBotStatsPair Exception")
            editBotStatsPair.second!!.printStackTrace()
        }

        File("botStats.json").writeText(gson.toJson(newBotStats))
    }

    private fun botStatsMessageForm(botStats: BotStats): String {
        return "Total: ${botStats.totalMatches}\n" + "Wins: ${botStats.wins}\n" + "Fails: ${botStats.fails}\n" + "WinRate: ${botStats.winRate}\n" + "Predicted: ${botStats.predicted}"
    }

    private fun getBotStats() : BotStats {
        val json = File("botStats.json").readText()
        val type = object : TypeToken<BotStats>() {}.type
        return gson.fromJson(json, type)
    }

    private suspend fun matchesForSave(bot: Bot) {
        val matchesForSave = liveMatches.second.filterNot { liveMatches.first.contains(it) }
        println("MATCHES FOR SAVE:")
        matchesForSave.forEach {
            println("${it.matchId}: ${it.firstTeam.name} VS ${it.secondTeam.name} ${it.tournament.name}: $it")
        }
        matchesForSave.forEach { match ->
            getMatchForSave(match)
        }

        println("FINISHED MATCHES:")
        finishedMatches.forEach {
            println("${it.matchId}: ${it.firstTeam.name} VS ${it.secondTeam.name} ${it.tournament.name}: $it")
        }

        for (finishedMatch in finishedMatches) {
            val messageId = GlobalVars.predictedMatches[finishedMatch.matchId]?.messageId
            val prediction = GlobalVars.predictedMatches[finishedMatch.matchId]?.prediction

            if(messageId == null) {
                println("messageId IS NULL: $finishedMatch")
            }

            if(prediction == null) {
                println("PREDICTION IS NULL: $finishedMatch")
                continue
            }

            updateBotStats(bot, finishedMatch, prediction)

            if(messageId != null) {
                val editPredictPair = bot.editMessageText(
                    chatId = ChatId.fromId(TEST_CHANNEL_ID),
                    messageId = messageId,
                    text = editMessageForm(finishedMatch, prediction)
                )

                when (editPredictPair.first?.isSuccessful) {
                    true -> {
                        println("editPredictPair.first?.isSuccessful == true")
                    }
                    false -> {
                        println("editPredictPair.first?.isSuccessful == false")
                    }
                    else -> {
                        println("editPredictPair.first?.isSuccessful == null")
                    }
                }

                if(editPredictPair.second == null) {
                    println("editPredictPair.second == null")
                } else {
                    println("editPredictPair Exception")
                    editPredictPair.second!!.printStackTrace()
                }
            }

            calibrator.execute(finishedMatch)
            PredictionPart.updateStats(prediction, finishedMatch, null)
        }

        GetPredictionPlayersGlickoUseCase.updateFiles()
        GetPredictionPlayersUseCase.updateFiles()
        botRepository.updatePlayers()
        GlobalVars.matches.addAll(0, finishedMatches)
        File("matches.json").writeText(gson.toJson(GlobalVars.matches))
    }

    private fun predictMessageForm(liveMatch: LiveMatch, prediction: Prediction): String {
        val predictText = if(prediction.willFirstTeamWin == true) "${liveMatch.firstTeam.name} VICTORY" else if(prediction.willFirstTeamWin == false) "${liveMatch.secondTeam.name} VICTORY" else "UNPREDICTABLE"
        val predictEmoji = if(predictText.contains("UNPREDICTABLE")) "◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F" else "\uD83D\uDD51\uD83D\uDD51\uD83D\uDD51\uD83D\uDD51\uD83D\uDD51\uD83D\uDD51"
        return "${liveMatch.tournament.name}\n\n${liveMatch.firstTeam.name} VS ${liveMatch.secondTeam.name}\n\nPREDICT: $predictText\n\nBEST OF: ${liveMatch.bestOf}\n$predictEmoji"
    }

    private fun editMessageForm(match: Match, prediction: Prediction): String {
        val predictText = if(prediction.willFirstTeamWin == true) "${match.firstTeam.name} VICTORY" else if(prediction.willFirstTeamWin == false) "${match.secondTeam.name} VICTORY" else "UNPREDICTABLE"
        val predictEmoji = if(predictText.contains("UNPREDICTABLE")) "◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F◻\uFE0F" else if(match.hasFirstTeamWon == prediction.willFirstTeamWin) "✅✅✅✅✅✅" else "\uD83D\uDEAB\uD83D\uDEAB\uD83D\uDEAB\uD83D\uDEAB\uD83D\uDEAB\uD83D\uDEAB"
        return "${match.tournament.name}\n\n${match.firstTeam.name} VS ${match.secondTeam.name} [${match.firstTeamScore} : ${match.secondTeamScore}]\n\nPREDICT: $predictText\n\nBEST OF: ${match.bestOf}\n$predictEmoji"
    }

    private suspend fun getMatchForSave(liveMatch: LiveMatch) {
        val matchDoc = getDocument(liveMatch.matchUrl)

        if(matchWasCancelled(matchDoc)) {
            println("getMatchForSave return: matchWasCancelled")
            return
        }
        val teamsBoxElement = matchDoc.select("div.standard-box.teamsBox")
        val firstTeamBoxElement = teamsBoxElement.select("div.team")[0]
        val secondTeamBoxElement = teamsBoxElement.select("div.team")[1]
        val firstTeamScore = firstTeamBoxElement.select("div.team1-gradient").select("div.lost, div.won, div.tie").text().toInt()
        val secondTeamScore = secondTeamBoxElement.select("div.team2-gradient").select("div.lost, div.won, div.tie").text().toInt()
        if(firstTeamScore == secondTeamScore) {
            println("getMatchForSave return: firstTeamScore == secondTeamScore")
            return
        }

        val finishedMatch = Match(
            matchId = liveMatch.matchId,
            bestOf = liveMatch.bestOf,
            tournament = liveMatch.tournament,
            firstTeam = liveMatch.firstTeam,
            secondTeam = liveMatch.secondTeam,
            firstTeamRanking = liveMatch.firstTeamRanking,
            secondTeamRanking = liveMatch.secondTeamRanking,
            firstTeamScore = firstTeamScore,
            secondTeamScore = secondTeamScore,
            hasFirstTeamWon = firstTeamScore > secondTeamScore,
            firstTeamPlayers = liveMatch.firstTeamPlayers,
            secondTeamPlayers = liveMatch.secondTeamPlayers
        )

        finishedMatches.add(finishedMatch)
    }

    private fun reconfigureMatchSets() {
        println("Reconfiguring match sets...")
        liveMatches.second.clear()
        liveMatches.second.addAll(liveMatches.first)
        liveMatches.first.clear()

        upcomingMatches.second.clear()
        upcomingMatches.second.addAll(upcomingMatches.first)
        upcomingMatches.first.clear()


        finishedMatches.clear()
    }

    private suspend fun headersSetup() {
        while (true) {
            try {
                println("Setting up headers...")

                println(BASE_HLTV_URL)

                val connection = Jsoup.connect(BASE_HLTV_URL)
                connection.userAgent(HLTV_USER_AGENT)

                delay(Random.nextLong(3500, 5000))
                connection.get()

                headers = connection.response().headers()

                return
            } catch (e: Exception) {
                println("headersSetup Exception")
                e.printStackTrace()
                println("Delaying...")
                delay(Random.nextLong(10000, 15000))
            }
        }
    }

    private suspend fun getMissedMatches() {
        try {
            println("Receiving missed matches has begun")
            val lastMatch = GlobalVars.matches.first()
            var offset = 0
            while (true) {
                val resultsDoc = getDocument("$BASE_HLTV_URL/results?offset=$offset", from = 200, until = 400)
                val matchesElements = resultsDoc.select("div.results-holder.allres").select("div.result-con").select("a.a-reset")
                for (matchElement in matchesElements) {
                    val matchId = extractSthFromUrl(matchElement.attr("href"), "matches/").toLong()
                    if(matchId == lastMatch.matchId) {
                        println("$matchId & ${lastMatch.matchId} equals")
                        saveMissedMatches()
                        return
                    }
                    missedMatchPage(matchElement)
                }
                offset += 100
            }
        } catch (e: Exception) {
            println("getMissedMatches Exception")
            e.printStackTrace()
            return
        }
    }

    private fun saveMissedMatches() {
        println("Saving missed matches...")
        missedMatches.forEach { missedMatch ->
            val prediction = predictor.getPredictionByMatch(missedMatch)
            calibrator.execute(missedMatch)
            PredictionPart.updateStats(prediction, missedMatch, null)
        }

        GetPredictionPlayersGlickoUseCase.updateFiles()
        GetPredictionPlayersUseCase.updateFiles()
        botRepository.updatePlayers()
        GlobalVars.matches.addAll(0, missedMatches)
        File("matches.json").writeText(gson.toJson(GlobalVars.matches))
    }

    private suspend fun missedMatchPage(matchElement: Element) {
        val matchUrl = BASE_HLTV_URL + matchElement.attr("href")
        val matchDoc = getDocument(matchUrl, from = 200, until = 400)
        if(matchWasCancelled(matchDoc)) {
            println("matchWasCancelled return: $matchUrl")
            return
        }
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
        if(firstTeamScore == secondTeamScore) {
            println("firstTeamScore == secondTeamScore return: $matchUrl")
            return
        }

        var firstTeamRanking: Int
        var secondTeamRanking: Int
        var firstTeamPlayers: List<Player>
        var secondTeamPlayers: List<Player>

        try {
            println("missedMatchPage TRY GET PLAYERS WITH LINEUP ELEMENT: $matchUrl")
            val firstTeamLineupElement = matchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[0]
            val secondTeamLineupElement = matchDoc.select("div#lineups.lineups").select("div.lineup.standard-box")[1]
            firstTeamRanking = getTeamRanking(firstTeamLineupElement)
            secondTeamRanking = getTeamRanking(secondTeamLineupElement)
            firstTeamPlayers = getTeamPlayersWithLineupElement(firstTeamLineupElement)
            secondTeamPlayers = getTeamPlayersWithLineupElement(secondTeamLineupElement)
            if(firstTeamPlayers.isEmpty() || secondTeamPlayers.isEmpty()) {
                println("missedMatchPage return: teamPlayer is empty: $matchUrl")
                return
            }
        } catch (e: IndexOutOfBoundsException) {
            println("missedMatchPage TRY GET PLAYERS WITH STATS ELEMENT: $matchUrl")
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
//            val resultsStats = singleMatchStatsElement.selectFirst("a.results-stats")
//            if(mapName.contains("Default") || resultsStats == null) return true
            if(mapName.contains("Default")) return true
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
        val liveMatchesContainers = matchesDoc.select("div.liveMatch-container")
        println("Fetching upcomingMatches...")
        for(container in upcomingMatchesContainers) {
            if(!isValidForPredict(container)) continue
            val upcomingMatchUrl = BASE_HLTV_URL + container.select("a.match.a-reset").attr("href")
            val upcomingMatchDoc = getDocument(upcomingMatchUrl)
            val tournament = getTournament(upcomingMatchDoc)
            if(tournament.tournamentId == 0L) {
                println("tournament.tournamentId == 0L: $upcomingMatchUrl")
                continue
            }
            matchPage(upcomingMatchDoc, upcomingMatchUrl, tournament, false)
        }
        println("Fetching liveMatches...")
        for(container in liveMatchesContainers) {
            if(container.attr("team1").isEmpty() || container.attr("team2").isEmpty()) continue
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
        val validMillisForPredict: Long = 15 * 60000 // The first number indicates how many minutes should remain before the start of the match for the prediction
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
        if(firstTeamPlayers.isEmpty() || secondTeamPlayers.isEmpty()) {
            println("matchPage TeamPlayers is EMPTY: $liveMatchUrl")
            return
        }

        if(isLiveMatch) {
            println("liveMatches.first.add: $liveMatchUrl")
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
            println("upcomingMatches.first.add: $liveMatchUrl")
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
                println("getDocument Exception")
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