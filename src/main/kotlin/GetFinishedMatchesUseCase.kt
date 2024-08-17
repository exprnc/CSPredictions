package com.exprnc.cspredictions

import com.exprnc.cspredictions.model.*
import com.exprnc.cspredictions.model.Map
import kotlinx.coroutines.*
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern
import kotlin.random.Random

class GetFinishedMatchesUseCase {

    private val chromeWebDriverPath = "C:\\Users\\exprn\\OneDrive\\Рабочий стол\\ChromeForTest\\chromedriver-win64\\chromedriver.exe"
    private val chromePath = "C:\\Users\\exprn\\OneDrive\\Рабочий стол\\ChromeForTest\\chrome-win64\\chrome.exe"

    private val baseUrl = "https://www.hltv.org"
    private val archiveUrl = "/events/archive"

    private val tournamentPages = 1 // 50 tournaments on one page

    private val driver by lazy { DriverCreator.get(chromePath, chromeWebDriverPath) }

    private val finishedMatches = mutableListOf<Match>()

    suspend fun execute() {
        coroutineScope {
            withContext(Dispatchers.IO) {
                try {
//                    var offset = 0
//                    for(page in 1..tournamentPages) {
//                        openPage("$baseUrl$archiveUrl?offset=$offset")
//                        tournamentsPage()
//                        offset += 50
//                    }
                    openPage("$baseUrl$archiveUrl")
                    tournamentsPage()
                }catch (e: Exception) {
                    e.printStackTrace()
                }finally {
//                    driver.quit()
                }
            }
        }
    }

    private suspend fun tournamentsPage() {
        val tournaments = driver.findElements(By.className("small-event"))
//        for(tournament in tournaments) {
//            val tournamentType = getTournamentType(tournament)
//            if(tournamentType == TournamentType.OTHER) continue
//            openPage(baseUrl + tournament.getAttribute("href"))
//            tournamentPage(tournamentType)
//        }
        val tournamentType = getTournamentType(tournaments[0])
        openPage(tournaments[0].getAttribute("href"))
        tournamentPage(tournamentType)
        finishedMatches.forEach {
            println("///////////////////////////////////////////////////////////////////")
            println(it)
        }
    }

    private fun getTournamentType(tournamentElement: WebElement): TournamentType {
        val tournamentTypeElement = tournamentElement.findElements(By.cssSelector("td"))[3]
        return TournamentType.entries.find { it.typeName == tournamentTypeElement.text } ?: TournamentType.OTHER
    }

    private suspend fun tournamentPage(tournamentType: TournamentType) {
        val tournamentId = extractSthFromUrl(driver.currentUrl, "events/").toLong()
        val tournamentName = driver.findElement(By.className("event-hub-title")).text
        val tournamentPrizePool = driver.findElement(By.className("prizepool")).getAttribute("title")
        val tournamentLocation = driver.findElement(By.cssSelector("td.location.gtSmartphone-only")).findElement(By.className("text-ellipsis")).text
        val tournamentMapPoolIds = getTournamentMapPoolIds()
        val tournamentFormats = getTournamentFormats()
        val tournamentAttendedTeamsIds = getTournamentAttendedTeamsIds()

        val tournament = Tournament(
            id = tournamentId,
            name = tournamentName,
            type = tournamentType,
            prizePool = tournamentPrizePool,
            location = tournamentLocation,
            mapPoolIds = tournamentMapPoolIds,
            formats = tournamentFormats,
            attendedTeamsIds = tournamentAttendedTeamsIds,
            isFinished = true,
        )

        openPage("$baseUrl/results?event=$tournamentId")

        tournamentResultsPage(tournament)
    }

    private suspend fun openPage(url: String) {
        driver.get(url)
        delay(Random.nextLong(2000, 4000))
        driverWait()
    }

    private fun toPreviousPage() {
        driver.navigate().back()
        driverWait()
    }

    private suspend fun tournamentResultsPage(tournament: Tournament) {
        val resultElements = driver.findElement(By.className("results-all")).findElements(By.className("a-reset"))
        resultElements.forEach { resultElement ->
            openPage(resultElement.getAttribute("href"))
            matchPage(tournament)
        }
    }

    private suspend fun matchPage(tournament: Tournament) {
        val matchId = extractSthFromUrl(driver.currentUrl, "matches/").toLong()
        val matchDescription = driver.findElement(By.className("preformatted-text")).text
        val matchBestOf = getMatchBestOf(matchDescription)
        val teamsBoxElement = driver.findElement(By.className("teamsBox"))
        val matchStartDateUnix = teamsBoxElement.findElement(By.className("date")).getAttribute("data-unix").toLong()
        val matchStartDate = convertUnixToString(matchStartDateUnix)
        val matchFirstTeamUrl = teamsBoxElement.findElements(By.className("team"))[0].findElement(By.cssSelector("a")).getAttribute("href")
        val matchSecondTeamUrl = teamsBoxElement.findElements(By.className("team"))[1].findElement(By.cssSelector("a")).getAttribute("href")
        val matchFirstTeamId = extractSthFromUrl(matchFirstTeamUrl, "team/").toLong()
        val matchSecondTeamId = extractSthFromUrl(matchSecondTeamUrl, "team/").toLong()
        val matchFirstTeamRanking = getTeamRanking(matchFirstTeamId, matchStartDate)
        val matchSecondTeamRanking = getTeamRanking(matchSecondTeamId, matchStartDate)
        val matchTier = getMatchTier(matchFirstTeamRanking, matchSecondTeamRanking)
        val matchFirstTeamScore = teamsBoxElement.findElements(By.className("team"))[0].findElement(By.className("team1-gradient")).findElement(By.cssSelector("div")).text.toLong()
        val matchSecondTeamScore = teamsBoxElement.findElements(By.className("team"))[1].findElement(By.className("team2-gradient")).findElement(By.cssSelector("div")).text.toLong()
        val matchHasFirstTeamWon = matchFirstTeamScore > matchSecondTeamScore
        val matchSingleMatches = getSingleMatches()

        val match = Match(
            id = matchId,
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
            hasFirstTeamWon = matchHasFirstTeamWon,
            singleMatches = matchSingleMatches,
            isFinished = true,
            startDate = matchStartDate,
        )
        finishedMatches.add(match)
    }

    private suspend fun getSingleMatches(): List<SingleMatch> {
        val singleMatches = mutableListOf<SingleMatch>()
        val singleMatchesStatsElements = driver.findElements(By.className("results-stats"))
        singleMatchesStatsElements.forEach { singleMatchElement ->
            val isFirstTeamPick = isFirstTeamPick(singleMatchElement, singleMatchesStatsElements.size)
            openPage(singleMatchElement.getAttribute("href"))
            singleMatches.add(singleMatchPage(isFirstTeamPick))
        }
        return singleMatches
    }

    private fun isFirstTeamPick(singleMatchElement: WebElement, elementsSize: Int): Boolean? {
        if(elementsSize == 1) return null
        val mapHolder = singleMatchElement.findElement(By.xpath("ancestor::*[contains(@class, 'mapholder')]"))
        val resultsLeftAttr = mapHolder.findElement(By.className("results-left")).getAttribute("class")
        return resultsLeftAttr.contains("pick")
    }

    private fun singleMatchPage(isFirstTeamPick: Boolean?): SingleMatch {
        val singleMatchId = extractSthFromUrl(driver.currentUrl, "mapstatsid/").toLong()
        val matchInfoBoxElement = driver.findElement(By.className("match-info-box-con"))
        val mapName = getTextWithoutChildrenElements(matchInfoBoxElement.findElement(By.className("match-info-box")))
        val singleMatchMap = getMapByName(mapName)
        val firstTeamElement = matchInfoBoxElement.findElement(By.className("team-left"))
        val firstTeamTagA = firstTeamElement.findElement(By.cssSelector("a"))
        val singleMatchFirstTeam = Team(id = extractSthFromUrl(firstTeamTagA.getAttribute("href"), "teams/").toLong(), name = firstTeamTagA.text)
        val secondTeamElement = matchInfoBoxElement.findElement(By.className("team-right"))
        val secondTeamTagA = secondTeamElement.findElement(By.cssSelector("a"))
        val singleMatchSecondTeam = Team(id = extractSthFromUrl(secondTeamTagA.getAttribute("href"), "teams/").toLong(), name = secondTeamTagA.text)
        val singleMatchFirstTeamScore = firstTeamElement.findElement(By.className("bold")).text.toLong()
        val singleMatchSecondTeamScore = secondTeamElement.findElement(By.className("bold")).text.toLong()
        val singleMatchHasFirstTeamWon = singleMatchFirstTeamScore > singleMatchSecondTeamScore
        val playersStatsElements = driver.findElements(By.className("stats-content"))
        val firstTeamPlayersStatsElement = playersStatsElements[0]
        val secondTeamPlayersStatsElement = playersStatsElements[3]
        val singleMatchFirstTeamPlayers = getTeamPlayers(firstTeamPlayersStatsElement)
        val singleMatchSecondTeamPlayers = getTeamPlayers(secondTeamPlayersStatsElement)
        val singleMatchScoreElement = driver.findElement(By.className("match-info-box-con")).findElement(By.className("match-info-row")).findElement(By.className("right"))
        val singleMatchScores = extractScores(singleMatchScoreElement.text)
        val firstTeamStartedForCT = singleMatchScoreElement.findElement(By.className("ct-color")).text.toLong() == singleMatchScores[0].firstTeamScore

        return SingleMatch(
            id = singleMatchId,
            map = singleMatchMap,
            firstTeam = singleMatchFirstTeam,
            secondTeam = singleMatchSecondTeam,
            firstTeamScore = singleMatchFirstTeamScore,
            secondTeamScore = singleMatchSecondTeamScore,
            hasFirstTeamWon = singleMatchHasFirstTeamWon,
            isFirstTeamPick = isFirstTeamPick,
            firstTeamPlayers = singleMatchFirstTeamPlayers,
            secondTeamPlayers = singleMatchSecondTeamPlayers,
            firstTeamStartedForCT = firstTeamStartedForCT,
            firstHalfScore = singleMatchScores.getOrNull(0),
            secondHalfScore = singleMatchScores.getOrNull(1),
            extraTimeScore = singleMatchScores.getOrNull(2),
            isFinished = true
        )
    }

    private fun extractScores(scoresText: String): List<Score> {
        val regex = "\\(\\s*(\\d+)\\s*:\\s*(\\d+)\\s*\\)".toRegex()
        val matches = regex.findAll(scoresText)
        return matches.map { matchResult ->
            val (first, second) = matchResult.destructured
            Score(firstTeamScore = first.toLong(), secondTeamScore = second.toLong())
        }.toList()
    }

    private fun getTeamPlayers(playersStatsElement: WebElement): List<Player> {
        val players = mutableListOf<Player>()
        playersStatsElement.findElements(By.cssSelector("tr")).forEach { playerStatsElement ->
            val playerInfoElement = playerStatsElement.findElement(By.className("st-player")).findElement(By.cssSelector("a"))
            val playerId = extractSthFromUrl(playerInfoElement.getAttribute("href"), "players/").toLong()
            val playerName = playerInfoElement.text
            val playerStats = PlayerStats(
                kills = getTextWithoutChildrenElements(playerStatsElement.findElement(By.className("st-kills"))).filter { it.isDigit() }.toLong(),
                deaths = playerStatsElement.findElement(By.className("st-deaths")).text.toLong(),
                firstKills = getFirstKills(playerStatsElement.findElement(By.className("st-fkdiff")).getAttribute("title")),
                firstDeaths = getFirstDeaths(playerStatsElement.findElement(By.className("st-fkdiff")).getAttribute("title")),
                headshotKills = playerStatsElement.findElement(By.className("st-kills")).findElement(By.cssSelector("span")).text.filter { it.isDigit() }.toLong(),
                assists = getTextWithoutChildrenElements(playerStatsElement.findElement(By.className("st-assists"))).filter { it.isDigit() }.toLong(),
                flashAssists = playerStatsElement.findElement(By.className("st-assists")).findElement(By.cssSelector("span")).text.filter { it.isDigit() }.toLong(),
                KAST = playerStatsElement.findElement(By.className("st-kdratio")).text.replace("%", "").toFloat(),
                ADR = playerStatsElement.findElement(By.className("st-adr")).text.toFloat(),
                rating = playerStatsElement.findElement(By.className("st-rating")).text.toFloat()
            )
            val player = Player(
                id = playerId,
                name = playerName,
                stats = playerStats
            )
            players.add(player)
        }
        return players
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

    private fun getFirstDeaths(title: String): Long {
        val pattern = Pattern.compile("(\\d+) first deaths")
        val matcher = pattern.matcher(title)

        return if (matcher.find()) {
            matcher.group(1).toLong()
        } else {
            -1
        }
    }

    private fun getMapByName(mapName: String): Map? {
        Map.entries.forEach { mapEntry ->
            if(mapName == mapEntry.name) return mapEntry
        }
        return null
    }

    private fun getTextWithoutChildrenElements(element: WebElement): String {
        val jsExecutor = driver as JavascriptExecutor
        val script = """
            var element = arguments[0];
            var text = "";
            var childNodes = element.childNodes;
            for (var i = 0; i < childNodes.length; i++) {
                var node = childNodes[i];
                if (node.nodeType === Node.TEXT_NODE) {
                    text += node.nodeValue;
                }
            }
            return text.trim();
        """
        return jsExecutor.executeScript(script, element) as String
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
        val worldRankingTeamsIdsByDate = mutableListOf<Long>()
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        val dateTime = LocalDateTime.parse(date, formatter)
        val localDate = dateTime.toLocalDate()
        val mondayOfWeek = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val year = mondayOfWeek.year
        val day = mondayOfWeek.dayOfMonth
        val month = mondayOfWeek.month.name.lowercase()
        openPage("$baseUrl/ranking/teams/$year/$month/$day")
        val rankedTeamsElements = driver.findElement(By.className("ranking")).findElements(By.className("ranked-team"))
        rankedTeamsElements.forEach { rankedTeam ->
            val rankedTeamUrl = rankedTeam.findElement(By.className("moreLink")).getAttribute("href")
            val rankedTeamId = extractSthFromUrl(rankedTeamUrl, "team/").toLong()
            worldRankingTeamsIdsByDate.add(rankedTeamId)
        }
        toPreviousPage()
        return worldRankingTeamsIdsByDate
    }

    private fun getMatchBestOf(matchDescription: String): Long? {
        return matchDescription.run {
            when {
                contains("Best of 1") -> 1
                contains("Best of 3") -> 3
                contains("Best of 5") -> 5
                contains("Best of 7") -> 7
                else -> null
            }
        }
    }

    private fun convertUnixToString(unixDate: Long): String {
        val instant = Instant.ofEpochMilli(unixDate)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneOffset.UTC)
        return formatter.format(instant)
    }

    private fun getTournamentAttendedTeamsIds(): List<Long> {
        val tournamentAttendedTeamsIds = mutableListOf<Long>()
        val tournamentAttendedTeamsElements = driver.findElement(By.className("teams-attending")).findElements(By.className("team-box"))
        tournamentAttendedTeamsElements.forEach { teamElement ->
            val teamUrl = teamElement.findElement(By.className("team-name")).findElement(By.ByTagName("a")).getAttribute("href")
            val teamId = extractSthFromUrl(teamUrl, "team/").toLong()
            tournamentAttendedTeamsIds.add(teamId)
        }
        return tournamentAttendedTeamsIds
    }

    private fun getTournamentFormats(): List<Format> {
        val formats = mutableListOf<Format>()
        val tournamentFormatNames = driver.findElement(By.className("formats")).findElements(By.className("format-header"))
        val tournamentFormatDescriptions = driver.findElement(By.className("formats")).findElements(By.className("format-data"))
        for ((name, description) in tournamentFormatNames.zip(tournamentFormatDescriptions)) {
            formats.add(
                Format(
                    name = name.text,
                    description = description.text
                )
            )
        }
        return formats
    }

    private fun getTournamentMapPoolIds(): List<Long> {
        val tournamentMapPoolElements = driver.findElements(By.className("map-pool-map-name"))
        val tournamentMapPoolIds = mutableListOf<Long>()
        Map.entries.forEach { map ->
            tournamentMapPoolElements.forEach { element ->
                if (map.name == element.text) tournamentMapPoolIds.add(map.id)
            }
        }
        return tournamentMapPoolIds
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

    private fun driverWait() {
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(10000))
    }
}