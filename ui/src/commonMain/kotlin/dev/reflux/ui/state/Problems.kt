package dev.reflux.ui.state

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlaybackVerdict
import dev.reflux.core.source.MediaSource
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.core.watch.ResumePoint
import dev.reflux.library.Library

/**
 * What Reflux tells the user when something cannot be done. Every failure the experience layer knows about
 * is worded here: what happened, in the user's terms, and what they can do about it. Technical detail goes
 * to [Platform.report] instead of the screen.
 */
internal object Problems {
    const val NOTHING_TO_PLAY = "There is nothing to play here yet."
    const val PLAYBACK_DID_NOT_START = "Playback couldn't start. Try again, or restart Reflux."
    const val LIBRARY_NOT_UPDATED = "Couldn't update the library. Reflux will try again."
    const val FOLDER_NOT_ADDED = "That folder couldn't be added."
    const val TRACK_GONE = "That track is no longer available."
    const val PLAYBACK_STALLED = "Playback isn't starting. The file may be slow to reach, or video couldn't be started on this device."

    fun noEngine(hint: String?): String = listOfNotNull("Reflux has no playback engine on this device yet.", hint).joinToString(" ")

    fun folder(choice: FolderChoice): String? = when (choice) {
        FolderChoice.Cancelled, is FolderChoice.Added -> null
        is FolderChoice.AlreadyInLibrary -> "That folder is already in your library, as part of ${choice.sourceName}."
        is FolderChoice.Unreadable ->
            "Reflux can't open “${choice.name}”. Check that it is connected and that you are allowed to read it."
        is FolderChoice.NotAFolder -> "“${choice.name}” isn't a folder Reflux can read media from."
    }

    /**
     * Said when playback continues on a copy that is not the same edit as the one the viewer was watching,
     * so that landing somewhere else than where they stopped does not go unexplained.
     */
    fun handoff(resume: ResumePoint): String? = when (resume.basis) {
        ResumeBasis.ADJUSTED -> "This is a different cut from the one you were watching. Playback picks up a little before where you stopped."
        ResumeBasis.NOT_TRANSFERABLE -> "This is a different cut from the one you were watching, so it starts from the beginning."
        else -> null
    }

    fun playback(failure: PlaybackFailure?): String = when (failure) {
        PlaybackFailure.UNREACHABLE -> "This file couldn't be opened. It may have been moved or deleted, or its drive or share was disconnected."
        PlaybackFailure.UNSUPPORTED -> "This file isn't in a format Reflux can play."
        PlaybackFailure.OUTPUT -> "Reflux couldn't start video or audio on this device."
        PlaybackFailure.UNKNOWN, null -> "Playback stopped unexpectedly."
    }
}

/** A playback failure as the player shows it: [message] for the user, [detail] from the engine for diagnostics. */
data class PlaybackError(val message: String, val detail: String?)

/** Why nothing of [itemId] can be played right now: no file, sources that are away, or formats this device lacks. */
internal fun Library.unplayableReason(itemId: MediaId, device: DeviceCapabilities, sources: (SourceId) -> MediaSource?): String {
    val ranked = selectVersion(itemId, device).ranked
    if (ranked.isEmpty()) return Problems.NOTHING_TO_PLAY
    val reachable = ranked.filter { it.candidate.availability != Availability.UNAVAILABLE && sources(it.version.location.sourceId) != null }
    if (reachable.isEmpty()) {
        val names = ranked.mapNotNull { source(it.version.location.sourceId)?.displayName }.distinct()
        return when (names.size) {
            0 -> "This can't be played right now. Its source is offline."
            1 -> "This can't be played right now. ${names.single()} is offline."
            else -> "This can't be played right now. ${names.joinToString(" and ")} are offline."
        }
    }
    val blocking = reachable.first().assessment.issues.firstOrNull { it.verdict == PlaybackVerdict.UNSUPPORTED }
    return blocking?.let { Formatting.issue(it) + "." } ?: "This can't be played right now."
}
