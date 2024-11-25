package scraping.coefs

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.EGamersMatch
import okio.utf8Size
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import utils.formatForUrl
import java.io.FileWriter
import kotlin.random.Random

class GetEGamersMatchesUseCase {

    private val baseUrl = "https://egamersworld.com"

    private val resultsPages = 250 // 15 matches on one page
    private val eGamersMatches = mutableListOf<EGamersMatch>()
    private var offset = 0

    fun execute() {
        runBlocking {
            try {
                println("Receiving eGamers matches has begun")
                for (page in 1..resultsPages) {
                    resultsPage(offset)
                    offset += 1
                }
                saveToJson()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                println("Receiving eGamers matches has ended")
            }
        }
    }

    private suspend fun resultsPage(offset: Int) {
        val resultsDoc = getDocument("$baseUrl/counterstrike/matches/history/page/$offset")
        val matchesElements = resultsDoc.select("[class*=item_matchBox]")
        for(matchElement in matchesElements) {
            println(offset)
            val oddsElements = matchElement.select("[class*=itemData_odds]")
            if(oddsElements.isEmpty()) continue

            val firstTeamName = matchElement.select("[class*=itemData_matchTeamName]")[0].text()
            val secondTeamName = matchElement.select("[class*=itemData_matchTeamName]")[1].text()
            val scoresElement = matchElement.select("[class*=itemData_matchResult]").text()
            if (scoresElement.contains("W") || scoresElement.contains("FF")) continue
            val scores = getScores(scoresElement)
            val firstTeamScore = scores.first
            val secondTeamScore = scores.second
            val bestOf = getMatchBestOf(matchElement.select("span.itemData_bestOfHome__gTFxW").text())
            val tournamentName = matchElement.select("span.itemData_matchStage__KWOOL").text()
            val eventLink = matchElement.select("[class*=item_eventName]").select("a")
            val eventLastName = eventLink.text().substringAfterLast(" ").replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
            val eventId = eventLink.attr("href").substringAfterLast("$eventLastName-")
            val matchId = matchElement.attr("id").substringAfterLast("upc_")
            val matchTitle = (matchElement.attr("title")).formatForUrl()
            val matchFirstUrl = "https://egamersworld.com/counterstrike/match/${eventLink.attr("href").extractEventId()}/$matchTitle-$matchId"
            val matchSecondUrl = "https://egamersworld.com/counterstrike/match/$eventId/$matchTitle-$matchId"
            matchPage(matchFirstUrl, matchSecondUrl, firstTeamName, secondTeamName, firstTeamScore, secondTeamScore, bestOf, tournamentName)
        }
    }

    private fun String.extractEventId(): String {
        val lastDashIndex = this.indexOfLast { it == '-' }
        if(lastDashIndex == this.utf8Size().toInt() - 1) {
            return this.substringBeforeLast("-").substringAfterLast("-") + "-"
        }
        else if(this[lastDashIndex - 1] == '-') {
            return "-" + this.substringAfterLast("-")
        }
        return this.substringAfterLast("-")
    }

    private fun getMatchBestOf(description: String): Int {
        return description.run {
            when {
                contains("Bo1") -> 1
                contains("Bo2") -> 2
                contains("Bo3") -> 3
                contains("Bo4") -> 4
                contains("Bo5") -> 5
                contains("Bo6") -> 6
                contains("Bo7") -> 7
                contains("Bo8") -> 8
                contains("Bo9") -> 9
                contains("Bo10") -> 10
                else -> 0
            }
        }
    }

    private suspend fun matchPage(
        matchFirstUrl: String,
        matchSecondUrl: String,
        firstTeamName: String,
        secondTeamName: String,
        firstTeamScore: Int,
        secondTeamScore: Int,
        bestOf: Int,
        tournamentName: String
    ) {
        var matchDoc = getDocument(matchFirstUrl)

        if(matchDoc.location() == "https://egamersworld.com/counterstrike/matches") {
            matchDoc = getDocument(matchSecondUrl)
            if(matchDoc.location() == "https://egamersworld.com/counterstrike/matches") {
                println("Введите корректный URL:")
                val correctUrl = readln()
                matchDoc = getDocument(correctUrl)
            }
        }

        val firstBookmakerElement = matchDoc.select("div.item_item__9DrM5")[0]
        val firstBookmakerLogoTitle = firstBookmakerElement.select("div.item_logo__E_Yaw").select("img").attr("title")
        val firstBookmakerDesc = firstBookmakerElement.select("[class*=item_short]")
        if(!firstBookmakerLogoTitle.contains("Bet365") || firstBookmakerDesc.isNotEmpty()) return
        val firstBookmakerCoefsElement = firstBookmakerElement.select("div.item_scores__4VPxw")[0].text()
        val (firstTeamCoef, secondTeamCoef) = firstBookmakerCoefsElement.split(" ").map { it.toDouble() }

        val eGamersMatch = EGamersMatch(
            firstTeamName = firstTeamName,
            secondTeamName = secondTeamName,
            firstTeamScore = firstTeamScore,
            secondTeamScore = secondTeamScore,
            hasFirstTeamWon = firstTeamScore > secondTeamScore,
            tournamentName = tournamentName,
            bestOf = bestOf,
            firstTeamCoef = firstTeamCoef,
            secondTeamCoef = secondTeamCoef
        )

        println(eGamersMatch)

        eGamersMatches.add(eGamersMatch)
    }

    private fun saveToJson() {
        val json = Gson().toJson(eGamersMatches)
        FileWriter("eGamersMatches.json").use { it.write(json) }
    }

    private fun getScores(scores: String): Pair<Int, Int> {
        val (first, second) = scores.split(":")
        return Pair(first.trim().toInt(), second.trim().toInt())
    }

    private suspend fun getDocument(url: String): Document {
        var currentUrl = url
        while (true) {
            try {
                println(currentUrl)

                val connection = Jsoup.connect(currentUrl)

                connection.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")

                val document: Document = connection.get()

                delay(1500)

                return document
            } catch (e: Exception) {
                e.printStackTrace()
                if(e.message?.contains("HTTP error fetching URL. Status=404") == true) {
                    println("Введите корректный URL:")
                    currentUrl = readln()
                    continue
                }
                println("Delaying...")
                delay(Random.nextLong(5000, 10000))
            }
        }
    }
}