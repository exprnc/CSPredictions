package utils

import model.Match
import java.text.DecimalFormat
import java.text.Normalizer

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

fun Double.round3(): Double {
    var formattedDouble = DecimalFormat("#0.000").format(this)
    formattedDouble = formattedDouble.replace(",", ".")
    return formattedDouble.toDouble()
}

fun String.formatForUrl(): String {
    // Словарь сокращений и их замены
    val replacements = mapOf(
        "\\bins\\b" to "insomnia" // Используем регулярное выражение для точного совпадения слова "ins"
    )

    // Приводим строку к нижнему регистру, нормализуем и заменяем диакритические знаки
    var formattedInput = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "") // Убираем диакритические знаки
        .trim()
        .replace(Regex("[^a-zA-Z0-9/\\- ]"), "") // Убираем все символы, кроме букв, цифр, дефисов и пробелов
        .replace("/", "-") // Заменяем слэши на дефисы
        .replace(Regex("\\s+"), "-") // Заменяем пробелы на дефисы
        .replace(Regex("-+"), "-") // Заменяем несколько подряд идущих дефисов одним
        .replace(Regex("^-+"), "") // Убираем дефисы в начале строки
        .lowercase() // Приводим к нижнему регистру

    // Применяем замены сокращений
    for ((key, value) in replacements) {
        formattedInput = formattedInput.replace(Regex(key), value) // Ищем точные совпадения слова
    }

    return formattedInput
}