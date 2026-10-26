package com.sozvezdie.messenger.core.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Форматирование времени для списка чатов и сообщений.
// Locale("ru") нужен, чтобы названия месяцев были русскими.

private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
private val dayFormat = SimpleDateFormat("d MMM", Locale("ru"))
private val fullDateFormat = SimpleDateFormat("d MMM yyyy", Locale("ru"))

/** Время в списке чатов: сегодня — часы, вчера — слово «вчера», дальше дата. */
fun formatChatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = millis }

    val sameDay = now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return timeFormat.format(Date(millis))

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val wasYesterday = yesterday.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    if (wasYesterday) return "вчера"

    return if (now.get(Calendar.YEAR) == date.get(Calendar.YEAR)) {
        dayFormat.format(Date(millis))
    } else {
        fullDateFormat.format(Date(millis))
    }
}

/** Время рядом с сообщением. */
fun formatMessageTime(millis: Long): String =
    if (millis <= 0L) "" else timeFormat.format(Date(millis))

/** Подпись «был(а) в сети» под именем собеседника. */
fun formatLastSeen(millis: Long): String {
    if (millis <= 0L) return "не в сети"
    val now = System.currentTimeMillis()
    val diffMinutes = (now - millis) / 60_000
    return when {
        diffMinutes < 1 -> "был(а) только что"
        diffMinutes < 60 -> "был(а) $diffMinutes мин назад"
        else -> "был(а) ${formatChatTime(millis)}"
    }
}
