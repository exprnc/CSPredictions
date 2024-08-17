package com.exprnc.cspredictions

import com.exprnc.cspredictions.cloudflare.ChromeDriverBuilder
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions

object DriverCreator {

    private fun createDriverUndetected(chromePath: String, webdriverPath: String): WebDriver {
        val chromeOptions = ChromeOptions()
        chromeOptions.setBinary(chromePath)
//        chromeOptions.addArguments("--auto-open-devtools-for-tabs")
//        chromeOptions.addArguments("--disable-dev-shm-usage")
//        chromeOptions.addArguments("--disable-software-rasterizer")
//        chromeOptions.addArguments("--disable-gpu")
//        chromeOptions.addArguments("--disable-extensions")
//        chromeOptions.addArguments("--no-sandbox")
//        chromeOptions.addArguments("--disable-infobars")
//        chromeOptions.addArguments("--disable-popup-blocking")
//        chromeOptions.addArguments("--incognito")
//        chromeOptions.addArguments("--disable-blink-features=AutomationControlled")
//        chromeOptions.addArguments("--headless")
//        chromeOptions.addArguments("--disable-images")
        return ChromeDriverBuilder().build(
            chromeOptions,
            driverExecutablePath = webdriverPath
        )
    }

    fun get(chromePath: String, webdriverPath: String): WebDriver {
        val driver = createDriverUndetected(chromePath, webdriverPath)
//        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20))
//        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20))
//        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(20))
//        val devTools: DevTools = driver.devTools
//        devTools.createSession()
//        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
//        devTools.addListener(Network.requestWillBeSent()) { request ->
//            println("Request: id ${request.requestId} ${request.request.url} ${request.request.headers} ")
//        }
//        devTools.addListener(Network.responseReceived()) { responseReceived ->
//            val requestId = responseReceived.requestId
//            if (responseReceived.response.status.toInt() == 200) {
//                devTools.send(Network.getResponseBody(requestId)).let { responseBody ->
//                    println("Response: id ${requestId} ${responseBody.body}")
//                }
//            }
//        }
        return driver
    }


}