package scraping

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import utils.CHROME_DRIVER_PATH
import utils.CHROME_PATH
<<<<<<< HEAD
import utils.HLTV_USER_AGENT
=======
>>>>>>> 30d2eaf6c61ab7dacfcc971e274163dbb516343c
import java.time.Duration

object SeleniumScraping {
    private val options by lazy { ChromeOptions() }
    val driver by lazy { ChromeDriver(options) }
    val wait by lazy { WebDriverWait(driver, Duration.ofSeconds(120)) }

    init {
        System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH)
        options.setBinary(CHROME_PATH)
<<<<<<< HEAD
        options.addArguments("--user-agent=$HLTV_USER_AGENT")
=======
>>>>>>> 30d2eaf6c61ab7dacfcc971e274163dbb516343c
    }
}