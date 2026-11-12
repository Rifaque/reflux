package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.ProfileId
import dev.reflux.core.watch.WatchRules

/**
 * Reflux's Continue Watching as a launcher shows it: the platform's own "continue" row (Watch Next on
 * Android TV and Google TV). It is a view of the canonical watch state, never a second record of it:
 * programs are derived from [Library.continuations] every time, the launcher's rows are only compared
 * with them, and nothing the launcher holds is ever read back into Reflux's state.
 */
object WatchNext {
    /** How many works Reflux puts on a launcher's row, which it shares with every other application. */
    const val LIMIT = 10

    /** The programs that should be on the row, most recently engaged first. */
    fun programs(continuations: List<Continuation>): List<WatchNextProgram> =
        continuations.sortedByDescending { it.lastEngagedAtEpochMs }.take(LIMIT).mapNotNull(::program)

    /**
     * What the launcher's row should show (docs/WATCH_STATE.md, "Profiles"): the active profile's Continue Watching,
     * each program naming that profile so that its card opens in it. A profile with a PIN shows nothing there: the
     * launcher is outside the lock, so a private profile's viewing is not put in front of whoever holds the remote.
     */
    fun programs(library: Library): List<WatchNextProgram> {
        val profile = library.activeProfile()
        if (library.profile(profile)?.hasPin != false) return emptyList()
        return programs(library.continuations(LIMIT)).map { it.copy(profile = profile) }
    }

    /** The program for one continuation, or null for a work that cannot be one (it has no playable kind). */
    fun program(continuation: Continuation): WatchNextProgram? {
        val entry = continuation.entry
        val state = entry.watchState
        // Where Play would start, by the same rule as everywhere else: a work started over, or one barely
        // begun, is the next thing to watch, not a place to continue from.
        val position = WatchRules.resumePosition(state)
        val type = if (position != null) WatchNextType.CONTINUE else WatchNextType.NEXT
        return when (val item = entry.item) {
            is Movie -> WatchNextProgram(
                itemId = item.id,
                type = type,
                kind = WatchNextProgram.Kind.MOVIE,
                title = item.title,
                year = item.year,
                positionMs = position,
                durationMs = state?.durationMs.takeIf { position != null },
                lastEngagedAtEpochMs = continuation.lastEngagedAtEpochMs,
                artwork = artworkFor(entry, show = null),
                description = entry.metadata?.overview,
            )
            is Episode -> WatchNextProgram(
                itemId = item.id,
                type = type,
                kind = WatchNextProgram.Kind.EPISODE,
                title = continuation.show?.item?.title ?: item.title,
                episodeTitle = item.episodeTitle,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                positionMs = position,
                durationMs = state?.durationMs.takeIf { position != null },
                lastEngagedAtEpochMs = continuation.lastEngagedAtEpochMs,
                artwork = artworkFor(entry, continuation.show),
                description = entry.metadata?.overview,
            )
            else -> null
        }
    }

    /**
     * What to do to the launcher's rows so that they show [desired] and nothing else of Reflux's.
     *
     * - A work that should be there and is not is added.
     * - One that is there with something changed (its position, the next episode's details, when it was last
     *   watched) is updated; one that is unchanged is left alone, so the launcher does not reorder or redraw
     *   what the user did not touch.
     * - One the user removed from the launcher (the launcher hides it) is deleted, as the platform expects,
     *   and is not offered again until the user plays it in Reflux again: [WatchNextPlan.dismissed] remembers
     *   when it was last played at the time. That is a choice about the launcher, not watch state; it never
     *   changes where the user is.
     * - Everything else of Reflux's on the row is deleted: works finished or gone, and duplicates.
     *
     * @param dismissed what the previous plan returned as [WatchNextPlan.dismissed].
     * @param fingerprint what a row stores to be compared by; a platform adds what it derives for the row
     *   itself (where it can read the artwork, once it has it).
     */
    fun plan(
        desired: List<WatchNextProgram>,
        published: List<PublishedProgram>,
        dismissed: Map<String, Long> = emptyMap(),
        fingerprint: (WatchNextProgram) -> String = { it.fingerprint },
    ): WatchNextPlan {
        val candidates = desired.associateBy { it.key }
        // A dismissal lasts until the work is played again, and is forgotten once it is not to be continued.
        val stillDismissed = dismissed.filter { (key, engaged) -> candidates[key]?.let { it.lastEngagedAtEpochMs <= engaged } == true }.toMutableMap()
        for (row in published) {
            val program = row.key?.let(candidates::get) ?: continue
            if (!row.browsable) stillDismissed[program.key] = program.lastEngagedAtEpochMs
        }
        val wanted = candidates - stillDismissed.keys
        val changes = mutableListOf<WatchNextChange>()
        val kept = mutableSetOf<String>()
        // A key published more than once keeps its oldest row.
        for (row in published.sortedBy { it.rowId }) {
            val program = row.key?.let(wanted::get)
            when {
                program == null || !row.browsable || row.key in kept -> changes += WatchNextChange.Remove(row.rowId)
                row.fingerprint != fingerprint(program) -> {
                    kept += row.key
                    changes += WatchNextChange.Update(row.rowId, program)
                }
                else -> kept += row.key
            }
        }
        desired.filter { it.key in wanted && it.key !in kept }.forEach { changes += WatchNextChange.Add(it) }
        return WatchNextPlan(changes, stillDismissed)
    }

    /**
     * The picture for a work's card. Launchers draw "continue" cards wide, so a wide image is preferred: an
     * episode's own still, then its show's backdrop; a film's backdrop. A poster is the fallback.
     */
    private fun artworkFor(entry: LibraryEntry, show: LibraryEntry?): WatchNextArtwork? {
        val wide = entry.artwork[ArtworkKind.THUMBNAIL] ?: show?.artwork?.get(ArtworkKind.BACKDROP) ?: entry.artwork[ArtworkKind.BACKDROP]
        if (wide != null) return WatchNextArtwork(wide, WatchNextArtwork.Shape.WIDE)
        val poster = entry.artwork[ArtworkKind.POSTER] ?: show?.artwork?.get(ArtworkKind.POSTER) ?: return null
        return WatchNextArtwork(poster, WatchNextArtwork.Shape.POSTER)
    }
}

enum class WatchNextType {
    /** The user stopped part-way through it. */
    CONTINUE,

    /** The next episode of a show the user is watching, not started yet. */
    NEXT,
}

data class WatchNextArtwork(val locator: ArtworkLocator, val shape: Shape) {
    enum class Shape { WIDE, POSTER }
}

/** One work on a launcher's "continue" row, in platform-neutral terms. */
data class WatchNextProgram(
    val itemId: MediaId,
    val type: WatchNextType,
    val kind: Kind,
    /** The film's title, or the show's for an episode. */
    val title: String,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
    /** Where Play continues, for [WatchNextType.CONTINUE]. */
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val lastEngagedAtEpochMs: Long,
    val artwork: WatchNextArtwork? = null,
    val description: String? = null,
    /** The profile whose Continue Watching this is: the card opens Reflux in it, without asking who is watching. */
    val profile: ProfileId? = null,
) {
    enum class Kind { MOVIE, EPISODE }

    /** What identifies the program on the launcher: the work, as Reflux's own media identity. */
    val key: String get() = itemId.value

    /**
     * Everything the launcher shows of the program, in one string, stored with the row so that a later
     * comparison can tell whether anything changed without reading the launcher's fields back.
     */
    val fingerprint: String
        get() = listOf(
            FINGERPRINT_VERSION, type, kind, title, episodeTitle, seasonNumber, episodeNumber, year, positionMs, durationMs,
            lastEngagedAtEpochMs, artwork?.shape, artwork?.locator, description, profile,
        ).joinToString("\u001F") { it?.toString().orEmpty() }

    private companion object {
        /** Changes when the shape of a program changes, so rows written by an older Reflux are rewritten. */
        const val FINGERPRINT_VERSION = 2
    }
}

/** A row of Reflux's on the launcher, as read back from it. */
data class PublishedProgram(
    val rowId: Long,
    /** [WatchNextProgram.key], or null for a row Reflux cannot attribute to a work. */
    val key: String?,
    val fingerprint: String?,
    /** False once the user removed it from the launcher, which then hides it. */
    val browsable: Boolean,
)

/** The changes to make to the launcher's rows, and the works the user removed from it to keep off it. */
data class WatchNextPlan(val changes: List<WatchNextChange>, val dismissed: Map<String, Long>)

sealed interface WatchNextChange {
    data class Add(val program: WatchNextProgram) : WatchNextChange
    data class Update(val rowId: Long, val program: WatchNextProgram) : WatchNextChange
    data class Remove(val rowId: Long) : WatchNextChange
}
