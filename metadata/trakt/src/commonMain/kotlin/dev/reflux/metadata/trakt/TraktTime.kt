package dev.reflux.metadata.trakt

import dev.reflux.core.model.CalendarDate

/** Wall time in milliseconds since the epoch. */
internal expect fun epochMillis(): Long

/** An ISO 8601 instant (`2026-11-15T12:00:00.000Z`) in milliseconds since the epoch; null if it is not one. */
internal expect fun parseInstantMillis(value: String?): Long?

/**
 * The calendar date of an ISO 8601 instant in the time zone [zone] (an IANA name such as `America/New_York`), or in
 * UTC when [zone] is missing or unknown. Trakt gives air times as UTC instants; a broadcast's date is local.
 */
internal expect fun localDate(instant: String?, zone: String?): CalendarDate?
