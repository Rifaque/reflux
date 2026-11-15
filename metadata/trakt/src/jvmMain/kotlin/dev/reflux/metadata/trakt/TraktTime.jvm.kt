package dev.reflux.metadata.trakt

import dev.reflux.core.model.CalendarDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal actual fun epochMillis(): Long = System.currentTimeMillis()

internal actual fun parseInstantMillis(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it.trim()).toEpochMilli() }.getOrNull() }

internal actual fun localDate(instant: String?, zone: String?): CalendarDate? {
    val moment = instant?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it.trim()) }.getOrNull() } ?: return null
    val id = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    val date = moment.atZone(id).toLocalDate()
    return CalendarDate(date.year, date.monthValue, date.dayOfMonth)
}
