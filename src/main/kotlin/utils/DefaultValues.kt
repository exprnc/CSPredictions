package utils

fun Byte?.orDefault(): Byte = this ?: 0

fun Short?.orDefault(): Short = this ?: 0

fun Int?.orDefault(): Int = this ?: 0

fun Long?.orDefault(): Long = this ?: 0

fun Float?.orDefault(): Float = this ?: 0f

fun Double?.orDefault(): Double = this ?: 0.0

fun String?.orDefault(): String = this ?: ""

fun Boolean?.orDefault(): Boolean = this ?: false