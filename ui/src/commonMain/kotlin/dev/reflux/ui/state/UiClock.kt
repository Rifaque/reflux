package dev.reflux.ui.state

import kotlin.time.TimeSource

/** Milliseconds on one monotonic clock, from the first time anyone asked: for deadlines that are only compared with each other. */
internal object UiClock {
    private val origin = TimeSource.Monotonic.markNow()

    fun nowMs(): Long = origin.elapsedNow().inWholeMilliseconds
}
