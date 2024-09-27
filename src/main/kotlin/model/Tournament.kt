package model

data class Tournament(
    val id: Long,
    val name: String,
    val type: String,
    val prizePool: Int,
    val isFinished: Boolean
)