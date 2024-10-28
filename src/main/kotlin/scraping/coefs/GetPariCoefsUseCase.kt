package scraping.coefs

import model.Match
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import scraping.SeleniumScraping.driver
import scraping.SeleniumScraping.wait

class GetPariCoefsUseCase {
    fun execute(match: Match): Pair<Double, Double> {
        try {
            driver.get("https://pari.ru/")

            val searchButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='btn.search']")))

            searchButton.click()

            val searchElement = driver.findElement(By.cssSelector("input.input--R2VmT"))

            searchElement.sendKeys("${match.firstTeam.name} ${match.secondTeam.name}")

            val matchButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.event-item--jju6N")))
            matchButton.click()

            val matchElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.table--_LdRe")))

            val firstTeamElementName = matchElement.findElements(By.cssSelector("div.text--dWt5e"))[0].text
            val secondTeamElementName = matchElement.findElements(By.cssSelector("div.text--dWt5e"))[1].text

            if (match.firstTeam.name.contains(firstTeamElementName, true) || firstTeamElementName.contains(match.firstTeam.name, true)) {
                val firstTeamCoef = matchElement.findElements(By.cssSelector("div.value--v77pD"))[0].text.toDouble()
                val secondTeamCoef = matchElement.findElements(By.cssSelector("div.value--v77pD"))[1].text.toDouble()
                return Pair(firstTeamCoef, secondTeamCoef)
            } else if (match.firstTeam.name.contains(secondTeamElementName, true) || secondTeamElementName.contains(match.firstTeam.name, true)) {
                val firstTeamCoef = matchElement.findElements(By.cssSelector("div.value--v77pD"))[1].text.toDouble()
                val secondTeamCoef = matchElement.findElements(By.cssSelector("div.value--v77pD"))[0].text.toDouble()
                return Pair(firstTeamCoef, secondTeamCoef)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(0.0, 0.0)
    }
}