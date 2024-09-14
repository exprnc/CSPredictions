package com.exprnc.cspredictions

import com.exprnc.cspredictions.model.*
import com.exprnc.cspredictions.model.Map
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.FileInputStream
import java.io.FileWriter
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern
import kotlin.random.Random

class GetFinishedMatchesUseCase {

    private val baseUrl = "https://www.hltv.org"
    private val archiveUrl = "/events/archive"
    private val tournamentPages = 25 // 50 tournaments on one page
    private val finishedMatches = mutableListOf<Match>()
    private val firestore by lazy { FirestoreClient.getFirestore() }
    private lateinit var headers: MutableMap<String, String>
    private var needCookie = true

    init {
        initFirestore()
    }

    private fun initFirestore() {
        val serviceAccount = FileInputStream("cs-predictions-e54b2-firebase-adminsdk-is8f9-6c059ed058.json")

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        FirebaseApp.initializeApp(options)
    }

    fun execute() {
        runBlocking {
            try {
                println("Receiving completed matches has begun")
                var offset = 0
                for (page in 1..tournamentPages) {
                    tournamentsPage(offset)
                    offset += 50
                }
                saveToJson()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                println("Receiving completed matches has ended")
            }
        }
    }

    private fun saveToJson() {
        val json = Gson().toJson(finishedMatches)
        FileWriter("matches.json").use {
            it.write(json)
        }
    }

    private suspend fun tournamentsPage(offset: Int) {
        val tournamentsDoc = getDocument("$baseUrl$archiveUrl?offset=$offset")
        val tournamentsElements = tournamentsDoc.select("a.a-reset.small-event.standard-box")
        for (tournamentElement in tournamentsElements) {
            val tournamentType = getTournamentType(tournamentElement)
            if(tournamentType == TournamentType.OTHER) continue
            tournamentPage(tournamentElement, tournamentType)
        }
    }

    private suspend fun tournamentPage(tournamentElement: Element, tournamentType: TournamentType) {
        val tournamentUrl = baseUrl + tournamentElement.attr("href")
        val tournamentDoc = getDocument(tournamentUrl)
        val tournamentId = extractSthFromUrl(tournamentUrl, "events/").toLong()
        val tournamentName = tournamentDoc.select("h1.event-hub-title").text()
        val tournamentPrizePool = tournamentDoc.select("td.prizepool.text-ellipsis").text()
        val tournamentLocation = tournamentDoc.select("td.location.gtSmartphone-only").select("span.text-ellipsis").text()
        val tournamentMapPoolIds = getTournamentMapPoolIds(tournamentDoc)
        val tournamentFormats = getTournamentFormats(tournamentDoc)
        val tournamentAttendedTeamsIds = getTournamentAttendedTeamsIds(tournamentDoc)

        val tournament = Tournament(
            id = tournamentId,
            url = tournamentUrl,
            name = tournamentName,
            type = tournamentType,
            prizePool = tournamentPrizePool,
            location = tournamentLocation,
            mapPoolIds = tournamentMapPoolIds,
            formats = tournamentFormats,
            attendedTeamsIds = tournamentAttendedTeamsIds,
            isFinished = true
        )

        tournamentResultsPage(tournament)
    }

    private suspend fun tournamentResultsPage(tournament: Tournament) {
        val resultsDoc = getDocument("$baseUrl/results?event=${tournament.id}")
        val resultsElements = resultsDoc.select("div.results-all").select("a.a-reset")
        for(resultElement in resultsElements) {
            val matchUrl = baseUrl + resultElement.attr("href")
            val matchDoc = getDocument(matchUrl)
            if(matchWasCancelled(matchDoc)) continue
            matchPage(matchDoc, matchUrl, tournament)
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

    private suspend fun matchPage(matchDoc: Document, matchUrl: String, tournament: Tournament) {
        val matchId = extractSthFromUrl(matchUrl, "matches/").toLong()
        val matchDescription = matchDoc.select("div.padding.preformatted-text").text()
        val matchBestOf = getMatchBestOf(matchDescription)
        val matchTeamsBoxElement = matchDoc.select("div.standard-box.teamsBox")
        val firstTeamBoxElement = matchTeamsBoxElement.select("div.team")[0]
        val secondTeamBoxElement = matchTeamsBoxElement.select("div.team")[1]
        val startDateUnix = matchTeamsBoxElement.select("div.time").attr("data-unix").toLong()
        val matchStartDate = convertUnixToString(startDateUnix)
        val firstTeamUrl = baseUrl + firstTeamBoxElement.select("a").attr("href")
        val secondTeamUrl = baseUrl + secondTeamBoxElement.select("a").attr("href")
        val matchFirstTeamId = extractSthFromUrl(firstTeamUrl, "team/").toLong()
        val matchSecondTeamId = extractSthFromUrl(secondTeamUrl, "team/").toLong()
        val matchFirstTeamRanking = getTeamRanking(matchFirstTeamId, matchStartDate)
        val matchSecondTeamRanking = getTeamRanking(matchSecondTeamId, matchStartDate)
        val matchTier = getMatchTier(matchFirstTeamRanking, matchSecondTeamRanking)
        val matchFirstTeamScore = firstTeamBoxElement.select("div.team1-gradient").select("div.lost, div.won").text().toLong()
        val matchSecondTeamScore = secondTeamBoxElement.select("div.team2-gradient").select("div.lost, div.won")[0].text().toLong()
        val hasFirstTeamWon = matchFirstTeamScore > matchSecondTeamScore
        val singleMatches = getSingleMatches(matchDoc)

        val match = Match(
            id = matchId,
            url = matchUrl,
            tournament = tournament,
            description = matchDescription,
            bestOf = matchBestOf,
            tier = matchTier,
            firstTeamId = matchFirstTeamId,
            secondTeamId = matchSecondTeamId,
            firstTeamRanking = matchFirstTeamRanking,
            secondTeamRanking = matchSecondTeamRanking,
            firstTeamScore = matchFirstTeamScore,
            secondTeamScore = matchSecondTeamScore,
            hasFirstTeamWon = hasFirstTeamWon,
            singleMatches = singleMatches,
            isFinished = true,
            startDate = matchStartDate
        )
        finishedMatches.add(match)
    }

    private suspend fun getSingleMatches(matchDoc: Document): List<SingleMatch> {
        val singleMatches = mutableListOf<SingleMatch>()
        val singleMatchesStatsElements = matchDoc.select("div.results.played")
        singleMatchesStatsElements.forEach { singleMatchStatsElement ->
            val isFirstTeamPick = isFirstTeamPick(singleMatchStatsElement, singleMatchesStatsElements.size)
            val singleMatchUrl = baseUrl + singleMatchStatsElement.select("a.results-stats").attr("href")
            singleMatches.add(singleMatchPage(singleMatchUrl, isFirstTeamPick))
        }
        return singleMatches
    }

    private suspend fun singleMatchPage(singleMatchUrl: String, isFirstTeamPick: Boolean?): SingleMatch {
        val singleMatchDoc = getDocument(singleMatchUrl)
        val singleMatchId = extractSthFromUrl(singleMatchUrl, "mapstatsid/").toLong()
        val infoBoxElement = singleMatchDoc.select("div.match-info-box-con")
        val singleMatchMapName = infoBoxElement.select("div.match-info-box").textNodes().joinToString(" ") { it.text().trim() }
        val singleMatchMap = getMapByName(singleMatchMapName)
        val firstTeamElement = infoBoxElement.select("div.team-left")
        val firstTeamTagA = firstTeamElement.select("a")
        val singleMatchFirstTeam = Team(id = extractSthFromUrl(firstTeamTagA.attr("href"), "teams/").toLong(), url = baseUrl + firstTeamTagA.attr("href"), name = firstTeamTagA.text())
        val secondTeamElement = infoBoxElement.select("div.team-right")
        val secondTeamTagA = secondTeamElement.select("a")
        val singleMatchSecondTeam = Team(id = extractSthFromUrl(secondTeamTagA.attr("href"), "teams/").toLong(), url = baseUrl + secondTeamTagA.attr("href"), name = secondTeamTagA.text())
        val singleMatchFirstTeamScore = firstTeamElement.select("div.bold").text().toLong()
        val singleMatchSecondTeamScore = secondTeamElement.select("div.bold").text().toLong()
        val hasFirstTeamWon = singleMatchFirstTeamScore > singleMatchSecondTeamScore
        val playersStatsElements = singleMatchDoc.select("div.stats-content")
        val firstTeamPlayersStatsElement = playersStatsElements[0]
        val secondTeamPlayersStatsElement = playersStatsElements[3]
        val singleMatchFirstTeamPlayers = getTeamPlayers(firstTeamPlayersStatsElement)
        val singleMatchSecondTeamPlayers = getTeamPlayers(secondTeamPlayersStatsElement)
        val scoreElement = infoBoxElement.select("div.match-info-row")[0].select("div.right")
        val singleMatchScores = extractScores(scoreElement.text())
        val firstTeamStartedForCT = scoreElement.select("span.ct-color")[0].text().toLong() == singleMatchScores[0].firstTeamScore

        val singleMatchRoundsHistory = getRoundsHistory(singleMatchDoc, false)
        if(singleMatchScores.getOrNull(2) != null) {
            val singleMatchOvertimeRoundsHistory = getRoundsHistory(singleMatchDoc, true)
            singleMatchRoundsHistory.addAll(singleMatchOvertimeRoundsHistory)
        }
        
        return SingleMatch(
            id = singleMatchId,
            url = singleMatchUrl,
            map = singleMatchMap,
            firstTeam = singleMatchFirstTeam,
            secondTeam = singleMatchSecondTeam,
            firstTeamScore = singleMatchFirstTeamScore,
            secondTeamScore = singleMatchSecondTeamScore,
            hasFirstTeamWon = hasFirstTeamWon,
            isFirstTeamPick = isFirstTeamPick,
            firstTeamPlayers = singleMatchFirstTeamPlayers,
            secondTeamPlayers = singleMatchSecondTeamPlayers,
            firstTeamStartedForCT = firstTeamStartedForCT,
            firstHalfScore = singleMatchScores.getOrNull(0),
            secondHalfScore = singleMatchScores.getOrNull(1),
            overtimeScore = singleMatchScores.getOrNull(2),
            roundsHistory = singleMatchRoundsHistory,
            isFinished = true
        )
    }

    private fun getRoundsHistory(singleMatchDoc: Document, needOvertimeHistory: Boolean): MutableList<RoundHistory> {
        val roundsHistory = mutableListOf<RoundHistory>()
        val roundsHistoryCon = if(needOvertimeHistory) {
            singleMatchDoc.select("div.standard-box.round-history-con.round-history-overtime")
        } else {
            singleMatchDoc.select("div.standard-box.round-history-con")
        }
        val firstTeamRoundsHistoryRow = roundsHistoryCon.select("div.round-history-team-row")[0]
        val secondTeamRoundsHistoryRow = roundsHistoryCon.select("div.round-history-team-row")[1]
        val firstTeamRoundsHistoryOutcomes = firstTeamRoundsHistoryRow.select("img.round-history-outcome")
        val secondTeamRoundsHistoryOutcomes = secondTeamRoundsHistoryRow.select("img.round-history-outcome")
        for ((firstOutcome, secondOutcome) in firstTeamRoundsHistoryOutcomes.zip(secondTeamRoundsHistoryOutcomes)) {
            val score: Score
            val hasFirstTeamWon: Boolean
            val reason: Reason?
            if(firstOutcome.attr("title").isEmpty() && secondOutcome.attr("title").isEmpty()) break
            if(firstOutcome.attr("title").isEmpty()) {
                val title = secondOutcome.attr("title")
                val src = secondOutcome.attr("src")
                score = getScore(title)
                hasFirstTeamWon = false
                reason = getReason(src)
            } else {
                val title = firstOutcome.attr("title")
                val src = firstOutcome.attr("src")
                score = getScore(title)
                hasFirstTeamWon = true
                reason = getReason(src)
            }
            roundsHistory.add(
                RoundHistory(
                    score = score,
                    hasFirstTeamWon = hasFirstTeamWon,
                    reason = reason
                )
            )
        }
        return roundsHistory
    }

    private fun getScore(title: String) : Score {
        val (firstTeamScore, secondTeamScore) = title.split("-")
        return Score(firstTeamScore.toLong(), secondTeamScore.toLong())
    }

    private fun getReason(src: String) : Reason? {
        return if(src.contains("t_win") || src.contains("ct_win")) {
            Reason.ALL_KILLED
        } else if(src.contains("bomb_exploded")) {
            Reason.BOMB_EXPLODED
        } else if(src.contains("bomb_defused")) {
            Reason.BOMB_DEFUSED
        } else if(src.contains("stopwatch")) {
            Reason.TIME_IS_UP
        } else {
            null
        }
    }

    private fun extractScores(scoresText: String): List<Score> {
        val regex = "\\(\\s*(\\d+)\\s*:\\s*(\\d+)\\s*\\)".toRegex()
        val matches = regex.findAll(scoresText)
        return matches.map { matchResult ->
            val (first, second) = matchResult.destructured
            Score(firstTeamScore = first.toLong(), secondTeamScore = second.toLong())
        }.toList()
    }

    private fun getTeamPlayers(playersStatsElement: Element): List<Player> {
        val players = mutableListOf<Player>()
        for (playerStatsElement in playersStatsElement.select("tbody").select("tr")) {
            if(playerStatsElement.select("td.st-adr").text() == "-") continue
            val playerInfoElement = playerStatsElement.select("td.st-player").select("a")
            val playerId = extractSthFromUrl(playerInfoElement.attr("href"), "players/").toLong()
            val playerName = playerInfoElement.text()
            val playerStats = PlayerStats(
                kills = getKills(playerStatsElement),
                deaths = playerStatsElement.select("td.st-deaths").text().toLong(),
                firstKills = getFirstKills(playerStatsElement.select("td.st-fkdiff").attr("title")),
                firstDeaths = getFirstDeaths(playerStatsElement.select("td.st-fkdiff").attr("title")),
                headshotKills = getHeadshotKills(playerStatsElement),
                assists = getAssists(playerStatsElement),
                flashAssists = getFlashAssists(playerStatsElement),
                KAST = playerStatsElement.select("td.st-kdratio").text().replace("%", "").toFloat(),
                ADR = playerStatsElement.select("td.st-adr").text().toFloat(),
                rating = playerStatsElement.select("td.st-rating").text().toFloat()
            )
            val player = Player(
                id = playerId,
                url = baseUrl + playerInfoElement.attr("href"),
                name = playerName,
                stats = playerStats
            )
            players.add(player)
        }
        return players
    }

    private fun getAssists(playerStatsElement: Element) : Long {
        return playerStatsElement.select("td.st-assists").textNodes()
            .joinToString(" ") { it.text().trim() }
            .filter { it.isDigit() }
            .toLong()
    }

    private fun getFlashAssists(playerStatsElement: Element) : Long {
        return playerStatsElement.select("td.st-assists").select("span")
            .text()
            .filter { it.isDigit() }
            .toLong()
    }

    private fun getHeadshotKills(playerStatsElement: Element) : Long {
        return playerStatsElement.select("td.st-kills").select("span")
            .text()
            .filter { it.isDigit() }.toLong()
    }

    private fun getFirstDeaths(title: String): Long {
        val pattern = Pattern.compile("(\\d+) first deaths")
        val matcher = pattern.matcher(title)

        return if (matcher.find()) {
            matcher.group(1).toLong()
        } else {
            -1
        }
    }

    private fun getFirstKills(title: String): Long {
        val pattern = Pattern.compile("(\\d+) first kills")
        val matcher = pattern.matcher(title)

        return if (matcher.find()) {
            matcher.group(1).toLong()
        } else {
            -1
        }
    }

    private fun getKills(playerStatsElement: Element) : Long {
        return playerStatsElement.select("td.st-kills").textNodes()
            .joinToString(" ") { it.text().trim() }
                .filter { it.isDigit() }
                .toLong()
    }

    private fun getMapByName(mapName: String): Map? {
        Map.entries.forEach { mapEntry ->
            if(mapName.contains(mapEntry.name)) return mapEntry
        }
        return null
    }

    private fun isFirstTeamPick(singleMatchElement: Element, elementsSize: Int): Boolean? {
        if(elementsSize == 1) return null
        val resultsLeftAttr = singleMatchElement.select("div.results-left").attr("class")
        return resultsLeftAttr.contains("pick")
    }

    private fun getMatchTier(
        firstTeamRanking: Long?,
        secondTeamRanking: Long?
    ): Long {
        val firstTeamRank = firstTeamRanking ?: Long.MAX_VALUE
        val secondTeamRank = secondTeamRanking ?: Long.MAX_VALUE

        return when {
            firstTeamRank <= 15 && secondTeamRank <= 15 -> 1
            firstTeamRank <= 30 && secondTeamRank <= 30 -> 2
            firstTeamRank <= 30 || secondTeamRank <= 30 -> 3
            else -> 4
        }
    }

    private suspend fun getTeamRanking(teamId: Long, matchStartDate: String): Long? {
        val worldRankingTeamsIdsByDate = getWorldRankingTeamsIdsByDate(matchStartDate)
        val index = worldRankingTeamsIdsByDate.indexOf(teamId)
        return if (index != -1) (index + 1).toLong() else null
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
        val rankingDoc = getDocument("$baseUrl/ranking/teams/$year/$month/$day")
        val rankedTeamsElements = rankingDoc.select("div.ranking").select("div.ranked-team.standard-box")
        rankedTeamsElements.forEach { rankedTeam ->
            val teamUrl = baseUrl + rankedTeam.select("a.moreLink").attr("href")
            val teamId = extractSthFromUrl(teamUrl, "team/").toLong()
            worldRankingTeamsIds.add(teamId)
        }
        return worldRankingTeamsIds
    }

//    private suspend fun getWorldRankingTeamsIdsByDate(date: String): List<Long> {
//        val worldRankingTeamsIds = mutableListOf<Long>()
//        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
//        val dateTime = LocalDateTime.parse(date, formatter)
//        var mondayOfWeek = dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
//        if (LocalDate.now().dayOfWeek == DayOfWeek.MONDAY) {
//            mondayOfWeek = mondayOfWeek.minusWeeks(1)
//        }
//        val year = mondayOfWeek.year
//        val day = mondayOfWeek.dayOfMonth
//        val month = mondayOfWeek.month.name.lowercase()
//        val rankingDoc = getDocument("$baseUrl/ranking/teams/$year/$month/$day")
//        val rankedTeamsElements = rankingDoc.select("div.ranking").select("div.ranked-team.standard-box")
//        rankedTeamsElements.forEach { rankedTeam ->
//            val teamUrl = baseUrl + rankedTeam.select("a.moreLink").attr("href")
//            val teamId = extractSthFromUrl(teamUrl, "team/").toLong()
//            worldRankingTeamsIds.add(teamId)
//        }
//        return worldRankingTeamsIds
//    }

    private fun convertUnixToString(unixDate: Long): String {
        val instant = Instant.ofEpochMilli(unixDate)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneOffset.UTC)
        return formatter.format(instant)
    }

    private fun getMatchBestOf(description: String): Long? {
        return description.run {
            when {
                contains("Best of 1") -> 1
                contains("Best of 3") -> 3
                contains("Best of 5") -> 5
                contains("Best of 7") -> 7
                else -> null
            }
        }
    }

    private fun getTournamentAttendedTeamsIds(tournamentDoc: Document): List<Long> {
        val attendedTeamsIds = mutableListOf<Long>()
        val attendedTeamsElements = tournamentDoc.select("div.col.standard-box.team-box.supports-hover")
        attendedTeamsElements.forEach { teamElement ->
            val teamUrl = baseUrl + teamElement.select("a").attr("href")
            val teamId = extractSthFromUrl(teamUrl, "team/").toLong()
            attendedTeamsIds.add(teamId)
        }
        return attendedTeamsIds
    }

    private fun getTournamentFormats(tournamentDoc: Document): List<Format> {
        val formats = mutableListOf<Format>()
        val formatNames = tournamentDoc.select("table.formats.table").select("th.format-header")
        val formatDescriptions = tournamentDoc.select("table.formats.table").select("td.format-data")
        for ((name, description) in formatNames.zip(formatDescriptions)) {
            formats.add(
                Format(
                    name = name.text(),
                    description = description.text()
                )
            )
        }
        return formats
    }

    private fun getTournamentMapPoolIds(tournamentDoc: Document): List<Long> {
        val mapPoolIds = mutableListOf<Long>()
        val mapPoolElements = tournamentDoc.select("div.map-pool-map-name")
        Map.entries.forEach { map ->
            mapPoolElements.forEach { element ->
                if (map.name == element.text()) mapPoolIds.add(map.id)
            }
        }
        return mapPoolIds
    }

    private fun getTournamentType(tournament: Element) : TournamentType {
        val typeElement = tournament.select("td.col-value.small-col.gtSmartphone-only")
        return TournamentType.entries.find { it.typeName == typeElement.text() } ?: TournamentType.OTHER
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
                if (needCookie) connection.header("Cookie", getFirestoreCookies())
                else connection.headers(headers)

                delay(Random.nextLong(100, 200))
                val document: Document = connection.get()

                headers = connection.response().headers()
                needCookie = false

                return document
            }catch (e: Exception) {
                e.printStackTrace()
                println("Delaying...")
                delay(Random.nextLong(5000, 10000))
            }
        }
    }

    private suspend fun getFirestoreCookies(): String {
        val cookieDocRef = firestore.collection("cookies").document("HLTV")
        val cookie = withContext(Dispatchers.IO) {
            cookieDocRef
                .get()
                .get()
        }.data
        return cookie?.getValue("cookie").toString()
    }
}