package dev.stade.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatChatTime(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val nowEpochMs = Clock.System.now().toEpochMilliseconds()
    val msg = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val now = kotlinx.datetime.Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(tz)

    val hh = msg.hour.toString().padStart(2, '0')
    val mm = msg.minute.toString().padStart(2, '0')
    val timeOnly = "$hh:$mm"

    val sameDay = msg.year == now.year && msg.dayOfYear == now.dayOfYear
    if (sameDay) return timeOnly

    val msgDate = LocalDateTime(msg.year, msg.monthNumber, msg.dayOfMonth, 0, 0)
    val nowDate = LocalDateTime(now.year, now.monthNumber, now.dayOfMonth, 0, 0)
    val diffDays = epochDays(nowDate) - epochDays(msgDate)
    if (diffDays == 1L) return "dün $timeOnly"

    val dd = msg.dayOfMonth.toString().padStart(2, '0')
    val mo = msg.monthNumber.toString().padStart(2, '0')
    if (msg.year == now.year) return "$dd.$mo $timeOnly"
    val yy = (msg.year % 100).toString().padStart(2, '0')
    return "$dd.$mo.$yy $timeOnly"
}

private fun epochDays(d: LocalDateTime): Long {
    val y = d.year.toLong()
    val m = d.monthNumber.toLong()
    val day = d.dayOfMonth.toLong()
    val a = (14 - m) / 12
    val y2 = y + 4800 - a
    val m2 = m + 12 * a - 3
    return day + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045
}

