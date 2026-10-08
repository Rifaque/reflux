package dev.reflux.core.model

/** Whether a source (and therefore its versions) can currently be reached. */
enum class Availability {
    AVAILABLE,
    UNAVAILABLE,

    /** Not yet checked since start-up. Cached library state remains browsable. */
    UNKNOWN,
}
