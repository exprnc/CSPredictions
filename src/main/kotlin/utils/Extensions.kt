package utils

import java.text.DecimalFormat

fun Double.round1(): Double {
    var formattedDouble = DecimalFormat("#0.0").format(this)
    formattedDouble = formattedDouble.replace(",", ".")
    return formattedDouble.toDouble()
}

fun Double.round2(): Double {
    var formattedDouble = DecimalFormat("#0.00").format(this)
    formattedDouble = formattedDouble.replace(",", ".")
    return formattedDouble.toDouble()
}

fun String?.prettify(): String = this.orEmpty().trim().lowercase()
fun String?.simplify(): String = this.orEmpty().replace(" ", "").lowercase()