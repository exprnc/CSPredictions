package com.exprnc.cspredictions

import com.exprnc.cspredictions.model.Score
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.IOException
import java.time.Duration

//suspend fun main() {
//    GetFinishedMatchesUseCase().execute()
//}

//private val chromeWebDriverKey = "webdriver.chrome.driver"
//private val chromeWebDriverPath = "C:\\Users\\exprn\\OneDrive\\Рабочий стол\\ChromeForTest\\chromedriver-win64\\chromedriver.exe"
//private val chromePath = "C:\\Users\\exprn\\OneDrive\\Рабочий стол\\ChromeForTest\\chrome-win64\\chrome.exe"
//private val driver by lazy { DriverCreator.get(chromePath, chromeWebDriverPath) }
//
//suspend fun main() {
//    coroutineScope {
//        withContext(Dispatchers.IO) {
//            driver.get("https://www.browserscan.net/bot-detection")
//            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(10000))
//            driver.quit()
//            Runtime.getRuntime().exec("taskkill /F /IM chrome.exe");
//        }
//    }
//}

fun main() {
    val url = "https://www.hltv.org/matches"
    val connection = Jsoup.connect(url)
    connection.header("User-Agent", "Mozilla")
    connection.cookie("__cf_bm", "44HWrKo9jg5VT1IuUNjWynNy8jPZbTykXOZby65U1xI-1723814370-1.0.1.1-xy9KAUMQfj1zpFj_ZDb9hXLEl.XHu3Aaqa7Gas8xSJGmH_nDhf_qMF4NC6I88AAMWg.DQ.SRlpsDB3AwBK7nfw")
    Thread.sleep(2000)
    val document = connection.get()
    println(document.select("a.match.a-reset"))
}