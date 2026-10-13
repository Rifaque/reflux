package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.offline.Download
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflinePlan
import dev.reflux.core.offline.OfflineReason
import dev.reflux.core.offline.OfflineSkipReason
import dev.reflux.core.source.SourceLocality
import dev.reflux.library.Library
import dev.reflux.library.Offline
import dev.reflux.library.downloads
import dev.reflux.library.downloadsOf

/** Where a work stands with being kept on the device, as a details page shows it. */
@Immutable
data class OfflineStatus(
    /** Whether there is anything to say or offer at all. */
    val offered: Boolean,
    /** True once a copy is on the device and nothing is still on its way. */
    val downloaded: Boolean,
    /** A download is waiting, under way, or paused. */
    val busy: Boolean,
    val failed: Boolean,
    /** What to say next to the action, when there is something to say. */
    val label: String?,
    val progress: Float?,
)

/** One line of the offline sheet: something proposed, or a download that exists. */
@Immutable
data class OfflineLine(
    val id: String,
    val title: String,
    val caption: String?,
    val detail: String,
    val state: DownloadState? = null,
    val progress: Float? = null,
)

@Immutable
data class OfflineModel(
    /** What Reflux would keep if asked now; null when there is nothing to propose. */
    val proposal: OfflinePlan?,
    val proposed: List<OfflineLine>,
    /** What was considered and left out, each as a sentence. */
    val leftOut: List<String>,
    /** "2.1 GB needed · room for 12 GB", for the proposal. */
    val room: String?,
    val downloads: List<OfflineLine>,
)

/** A work's title as a line says it: "Heat", or "Breaking Bad" with "S1 · E2 · Cat's in the Bag..." beneath. */
private fun Library.lineTitle(itemId: MediaId): Pair<String, String?> = item(itemId)?.let { heading(it) } ?: ("No longer in your library" to null)

internal fun Library.offlineStatus(itemId: MediaId, offline: Offline): OfflineStatus {
    val downloads = downloadsOf(itemId)
    val waiting = downloads.firstOrNull { it.state == DownloadState.DOWNLOADING } ?: downloads.firstOrNull { it.active } ?: downloads.firstOrNull { it.state == DownloadState.PAUSED }
    val failed = downloads.firstOrNull { it.state == DownloadState.FAILED || it.state == DownloadState.UNAVAILABLE }
    val completed = downloads.count { it.state == DownloadState.COMPLETED }
    // A film that is on the device as a file of the user's own has nothing to gain from a copy.
    val alreadyHere = downloads.isEmpty() && item(itemId) is Movie &&
        versions(itemId).any { it.locality == SourceLocality.DEVICE && it.availability != Availability.UNAVAILABLE }
    return OfflineStatus(
        offered = downloads.isNotEmpty() || (offline.possible() && !alreadyHere),
        downloaded = completed > 0 && waiting == null && failed == null,
        busy = waiting != null,
        failed = waiting == null && failed != null,
        label = when {
            waiting != null -> Formatting.downloadState(waiting)
            failed != null -> "Download didn't finish"
            completed > 1 -> "$completed episodes on this device"
            completed == 1 -> "On this device"
            else -> null
        },
        progress = waiting?.progress,
    )
}

internal fun Library.offlineModel(offline: Offline): OfflineModel {
    val plan = offline.suggest()
    fun line(download: Download): OfflineLine {
        val (title, caption) = lineTitle(download.itemId)
        return OfflineLine(download.id, title, caption, Formatting.downloadDetail(download), download.state, download.progress.takeIf { download.state != DownloadState.COMPLETED })
    }
    return OfflineModel(
        proposal = plan.takeUnless { it.empty },
        proposed = plan.downloads.map { planned ->
            val (title, caption) = lineTitle(planned.itemId)
            val why = if (planned.reason == OfflineReason.NEXT_EPISODE) "Next episode" else "Continue watching"
            OfflineLine(planned.itemId.value, title, listOfNotNull(caption, why).joinToString("  ·  "), (if (planned.sizeKnown) "" else "about ") + Formatting.size(planned.bytes))
        },
        // Only what the user could not have guessed: that something is already here needs no saying.
        leftOut = plan.skipped.filter { it.reason != OfflineSkipReason.ALREADY_ON_DEVICE && it.reason != OfflineSkipReason.ALREADY_DOWNLOADING }.map { skip ->
            val (title, caption) = lineTitle(skip.itemId)
            listOfNotNull(title, caption).joinToString(" · ") + ": " + Problems.offlineSkipShort(skip.reason)
        },
        room = plan.takeUnless { it.empty }?.let { "${Formatting.size(it.requiredBytes)} needed  ·  room for ${Formatting.size(it.usableBytes)}" },
        downloads = downloads().sortedByDescending { it.createdAtEpochMs }.map(::line),
    )
}

internal fun Formatting.downloadState(download: Download): String = when (download.state) {
    DownloadState.QUEUED, DownloadState.PREPARING -> "Waiting to download"
    DownloadState.DOWNLOADING -> download.progress?.let { "Downloading  ·  ${(it * 100).toInt()}%" } ?: "Downloading"
    DownloadState.PAUSED -> "Download paused"
    DownloadState.COMPLETED -> "On this device"
    DownloadState.FAILED -> Problems.download(download.failure)
    DownloadState.CANCELLED -> "Cancelled"
    DownloadState.UNAVAILABLE -> "The copy is no longer on this device"
}

internal fun Formatting.downloadDetail(download: Download): String = when (download.state) {
    DownloadState.COMPLETED -> listOfNotNull("On this device", download.totalBytes?.let(::size)).joinToString("  ·  ")
    DownloadState.DOWNLOADING -> listOfNotNull(downloadState(download), download.totalBytes?.let { "of ${size(it)}" }).joinToString(" ")
    else -> downloadState(download)
}

internal fun Problems.offlineSkip(reason: OfflineSkipReason): String = when (reason) {
    OfflineSkipReason.ALREADY_ON_DEVICE -> "This is already on this device."
    OfflineSkipReason.ALREADY_DOWNLOADING -> "This is already being downloaded."
    OfflineSkipReason.NOT_ENOUGH_STORAGE -> "There isn't enough room on this device to keep this."
    OfflineSkipReason.SOURCE_UNAVAILABLE -> "This can't be downloaded right now. Its source is offline."
    OfflineSkipReason.NOT_DOWNLOADABLE -> "Its source doesn't allow keeping a copy."
    OfflineSkipReason.NOT_PLAYABLE_HERE -> "No version of this plays on this device."
}

internal fun Problems.offlineSkipShort(reason: OfflineSkipReason): String = when (reason) {
    OfflineSkipReason.ALREADY_ON_DEVICE -> "already on this device"
    OfflineSkipReason.ALREADY_DOWNLOADING -> "already downloading"
    OfflineSkipReason.NOT_ENOUGH_STORAGE -> "not enough room"
    OfflineSkipReason.SOURCE_UNAVAILABLE -> "its source is offline"
    OfflineSkipReason.NOT_DOWNLOADABLE -> "its source doesn't allow copies"
    OfflineSkipReason.NOT_PLAYABLE_HERE -> "no version plays on this device"
}

internal fun Problems.download(failure: DownloadFailure?): String = when (failure) {
    DownloadFailure.INSUFFICIENT_STORAGE -> "Not enough room on this device"
    DownloadFailure.SOURCE_UNAVAILABLE -> "Waiting for its source to come back"
    DownloadFailure.INTERRUPTED -> "The connection was lost"
    DownloadFailure.STORAGE_UNAVAILABLE -> "This device's storage can't be written to"
    DownloadFailure.MEDIA_GONE -> "Its source no longer has the file"
    DownloadFailure.NOT_ALLOWED -> "Its source didn't allow the download"
    DownloadFailure.UNKNOWN, null -> "The download didn't finish"
}
