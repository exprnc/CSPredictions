package scraping.coefs

import model.Match
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import scraping.SeleniumScraping.wait
import scraping.SeleniumScraping.driver

class GetBetBoomCoefsUseCase {
    fun execute(match: Match): Pair<Double, Double> {
        try {
            driver.get("https://betboom.ru/esport")

            val searchElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input.eynZZ")))
            searchElement.sendKeys("${match.firstTeam.name} ${match.secondTeam.name}")

            val matchElement = driver.findElement(By.cssSelector("div.Ur2bE"))
            val firstTeamElementName = matchElement.findElements(By.cssSelector("span.rzys6"))[0].text
            val secondTeamElementName = matchElement.findElements(By.cssSelector("span.rzys6"))[1].text

            if (match.firstTeam.name.contains(firstTeamElementName, true) || firstTeamElementName.contains(match.firstTeam.name, true)) {
                val firstTeamCoef = matchElement.findElements(By.cssSelector("button.yQjLH.frA0U"))[0].findElement(By.cssSelector("span.do7iP")).text.toDouble()
                val secondTeamCoef = matchElement.findElements(By.cssSelector("button.yQjLH.frA0U"))[1].findElement(By.cssSelector("span.do7iP")).text.toDouble()
                return Pair(firstTeamCoef, secondTeamCoef)
            } else if (match.firstTeam.name.contains(secondTeamElementName, true) || secondTeamElementName.contains(match.firstTeam.name, true)) {
                val firstTeamCoef = matchElement.findElements(By.cssSelector("button.yQjLH.frA0U"))[1].findElement(By.cssSelector("span.do7iP")).text.toDouble()
                val secondTeamCoef = matchElement.findElements(By.cssSelector("button.yQjLH.frA0U"))[0].findElement(By.cssSelector("span.do7iP")).text.toDouble()
                return Pair(firstTeamCoef, secondTeamCoef)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(0.0, 0.0)
    }
}