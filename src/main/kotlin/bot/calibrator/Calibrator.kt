package bot.calibrator

import model.Match
import bot.GlobalVars

class Calibrator {
    private val calibratePlayers = CalibratePlayersUseCase()
    private val calibratePlayersGlicko = CalibratePlayersGlickoUseCase()

    fun execute(match: Match) {
        calibratePlayers.execute(match)
        calibratePlayersGlicko.execute(match)
    }
}
