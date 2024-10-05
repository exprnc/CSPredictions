package testpredict

import model.Match

class Calibrator {
    private val calibratePlayers = CalibratePlayersUseCase()
    private val calibratePlayersGlicko = CalibratePlayersGlickoUseCase()

    fun execute(match: Match) {
        if (GlobalVars.getTier(match) !in 1..2) return
        calibratePlayers.execute(match)
        calibratePlayersGlicko.execute(match)
    }
}
