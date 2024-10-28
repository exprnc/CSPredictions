package bot.calibrator

import model.Match
import model.Rating
import bot.GlobalVars
import model.PlayerStats
import kotlin.math.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class CalibratePlayersGlickoUseCase {

    private val glicko2 = Glicko2()

    fun execute(match: Match) {
        val firstTeamValue = Rating(
            mu = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.firstTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        val secondTeamValue = Rating(
            mu = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.mu ?: Rating.MU } / 5,
            phi = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.phi ?: Rating.PHI } / 5,
            sigma = match.secondTeamPlayers.sumOf { GlobalVars.players[it.playerId]?.glicko?.sigma ?: Rating.SIGMA } / 5,
        )
        if (!(firstTeamValue.phi > 3000 || firstTeamValue.phi <= 3000) || !(secondTeamValue.phi > 3000 || secondTeamValue.phi <= 3000)) {
            return
        }
        if (firstTeamValue.phi > 3000)
        changeRating(match.firstTeamPlayers.map { it.playerId }, secondTeamValue, match.hasFirstTeamWon)
        changeRating(match.secondTeamPlayers.map { it.playerId }, firstTeamValue, !match.hasFirstTeamWon)
    }

    private fun changeRating(
        players: List<Long>,
        enemyTeamRating: Rating,
        hasTeamWon: Boolean
    ) {
        for (player in players) {
            val playerItem = (GlobalVars.players[player] ?: PlayerStats(player)).apply {
                val winner = if (hasTeamWon) glicko else enemyTeamRating
                val loser = if (!hasTeamWon) glicko else enemyTeamRating
                val result = glicko2.rate1vs1(winner, loser)
                glicko = if (winner == glicko) result.first else result.second
            }
            GlobalVars.players[player] = playerItem
        }
    }
}

class Glicko2(
    private val mu: Double = Rating.MU,
    private val tau: Double = Rating.TAU,
    private val epsilon: Double = Rating.EPSILON
) {

    companion object {
        fun createRating(mu: Double = Rating.MU, phi: Double = Rating.PHI, sigma: Double = Rating.SIGMA): Rating {
            return Rating(mu, phi, sigma)
        }
    }

    private fun scaleDown(rating: Rating, ratio: Double = 173.7178): Rating {
        val mu = (rating.mu - this.mu) / ratio
        val phi = rating.phi / ratio
        return createRating(mu, phi, rating.sigma)
    }

    private fun scaleUp(rating: Rating, ratio: Double = 173.7178): Rating {
        val mu = rating.mu * ratio + this.mu
        val phi = rating.phi * ratio
        return createRating(mu, phi, rating.sigma)
    }

    private fun reduceImpact(rating: Rating): Double {
        return 1.0 / sqrt(1 + (3 * rating.phi * rating.phi) / (Math.PI * Math.PI))
    }

    private fun expectScore(rating: Rating, otherRating: Rating, impact: Double): Double {
        return 1.0 / (1 + exp(-impact * (rating.mu - otherRating.mu)))
    }

    private fun determineSigma(rating: Rating, difference: Double, variance: Double): Double {
        val phi = rating.phi
        val differenceSquared = difference * difference

        val alpha = ln(rating.sigma * rating.sigma)

        fun f(x: Double): Double {
            val tmp = phi * phi + variance + exp(x)
            val a = exp(x) * (differenceSquared - tmp) / (2 * tmp * tmp)
            val b = (x - alpha) / (tau * tau)
            return a - b
        }

        var a = alpha
        var b: Double
        b = if (differenceSquared > phi * phi + variance) {
            ln(differenceSquared - phi * phi - variance)
        } else {
            var k = 1
            while (f(alpha - k * sqrt(tau * tau)) < 0) {
                k++
            }
            alpha - k * sqrt(tau * tau)
        }

        var (fA, fB) = f(a) to f(b)

        while (abs(b - a) > epsilon) {
            val c = a + (a - b) * fA / (fB - fA)
            val fC = f(c)
            if (fC * fB < 0) {
                a = b
                fA = fB
            } else {
                fA /= 2
            }
            b = c
            fB = fC
        }

        return exp(1.0).pow(a / 2)
    }

    private fun rate(_rating: Rating, series: List<Pair<Double, Rating>>): Rating {
        val rating = scaleDown(_rating)

        var varianceInv = 0.0
        var difference = 0.0

        if (series.isEmpty()) {
            val phiStar = sqrt(rating.phi * rating.phi + rating.sigma * rating.sigma)
            return scaleUp(createRating(rating.mu, phiStar, rating.sigma))
        }

        for ((actualScore, _otherRating) in series) {
            val otherRating = scaleDown(_otherRating)
            val impact = reduceImpact(otherRating)
            val expectedScore = expectScore(rating, otherRating, impact)
            varianceInv += impact * impact * expectedScore * (1 - expectedScore)
            difference += impact * (actualScore - expectedScore)
        }

        difference /= varianceInv
        val variance = 1.0 / varianceInv

        val sigma = determineSigma(rating, difference, variance)
        val phiStar = sqrt(rating.phi * rating.phi + sigma * sigma)

        val phi = 1.0 / sqrt(1 / (phiStar * phiStar) + 1 / variance)
        val mu = rating.mu + phi * phi * difference / variance

        return scaleUp(createRating(mu, phi, sigma))
    }

    fun rate1vs1(rating1: Rating, rating2: Rating, drawn: Boolean = false): Pair<Rating, Rating> {
        return Pair(
            rate(rating1, listOf((if (drawn) Rating.DRAW else Rating.WIN) to rating2)),
            rate(rating2, listOf((if (drawn) Rating.DRAW else Rating.LOSS) to rating1))
        )
    }

    fun quality1vs1(rating1: Rating, rating2: Rating): Double {
        val expectedScore1 = expectScore(rating1, rating2, reduceImpact(rating1))
        val expectedScore2 = expectScore(rating2, rating1, reduceImpact(rating2))
        val expectedScore = (expectedScore1 + expectedScore2) / 2
        return 2 * (0.5 - abs(0.5 - expectedScore))
    }
}