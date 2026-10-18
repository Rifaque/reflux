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
    const val STORAGE_NOT_ALLOWED = "Reflux needs your permission to read this device's storage before it can find your media."
    const val FOLDER_NOT_OPENED = "That folder couldn't be opened."
    const val OFFLINE_PREPARING = "Preparing the best offline version for this device."
    const val OFFLINE_PREPARING_TO_FIT = "Preparing the best offline version that fits on this device."
    const val OFFLINE_NOT_PREPARED = "That couldn't be prepared for offline."
    const val OFFLINE_RESUMED = "Picking up where the download stopped."
    const val OFFLINE_PIN_SET_ASIDE = "The version you chose can't be downloaded right now. This is the best one that can be."
    const val DOWNLOAD_NOT_CHANGED = "That download couldn't be changed."
    const val ENTER_ADDRESS = "Enter the address first."
    const val ENTER_USERNAME = "Enter the user name you use for this server."
    const val SOURCE_NOT_REMOVED = "That source couldn't be removed."
    const val TRACK_GONE ="That track is no longer available."
    const val PLAYBACK_STALLED = "Playback isn't starting. The file may be slow to reach, or video couldn't be started on this device."
    const val SOURCE_STOPPED = "The source stopped sending this. It may be asleep, busy, or no longer connected."
    const val IDENTIFY_SEARCH = "That search didn't work. Try again in a moment."
    const val IDENTIFY_APPLY = "That couldn't be changed. Your library is as it was."

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

/**
 * A playback failure as the player shows it: [message] for the user, [detail] from the engine for diagnostics.
 * [canRetry] offers Try again, for failures that may pass (a source that stopped answering or sending).
 */
data class PlaybackError(val message: String, val detail: String?, val canRetry: Boolean = false)

/** Why nothing of [itemId] can be played right now: no file, sources that are away, or formats this device lacks. */
internal fun Library.unplayableReason(itemId: MediaId, device: DeviceCapabilities, sources: (SourceId) -> MediaSource?): String {
    val ranked = selectVersion(itemId, device).ranked
    if (ranked.isEmpty()) return Problems.NOTHING_TO_PLAY
    val reachable = ranked.filter { it.candidate.availability != Availability.UNAVAILABLE && sources(it.version.location.sourceId) != null }
    if (reachable.isEmpty()) {
        val names = ranked.mapNotNull { source(it.version.location.sourceId)?.displayName }.distinct()
        return when (names.size) {
            0 -> "This can't be played right now. Its source isn't connected."
            1 -> "This can't be played right now. ${names.single()} isn't connected."
            else -> "This can't be played right now. ${names.joinToString(" and ")} aren't connected."
        }
    }
    val blocking = reachable.first().assessment.issues.firstOrNull { it.verdict == PlaybackVerdict.UNSUPPORTED }
    return blocking?.let { Formatting.issue(it) + "." } ?: "This can't be played right now."
}
