package model

data class Rating(val mu: Double = MU, val phi: Double = PHI, val sigma: Double = SIGMA) {
    companion object {
        const val WIN = 1.0
        const val DRAW = 0.5
        const val LOSS = 0.0

        const val MU = 1500.0
        const val PHI = 350.0
        const val SIGMA = 0.06
        const val TAU = 1.0
        const val EPSILON = 0.000001
    }
}