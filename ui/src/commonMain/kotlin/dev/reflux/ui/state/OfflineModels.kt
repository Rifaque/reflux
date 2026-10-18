package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.offline.Download
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflineChoice
import dev.reflux.core.offline.OfflinePlan
import dev.reflux.core.offline.OfflineReason
import dev.reflux.core.offline.OfflineSkipReason
import dev.reflux.core.source.SourceLocality
import dev.reflux.library.Library
import dev.reflux.library.Offline
import dev.reflux.library.copyOf
import dev.reflux.library.downloads
import dev.reflux.library.downloadsOf
import dev.reflux.library.offlineCoverage
import dev.reflux.library.pinnedVersions
import dev.reflux.library.episodesOffDevice

/** Where a work stands with being kept on the device, as a details page shows it. */
@Immutable
data class OfflineStatus(
    /** Whether there is anything to say or offer at all. */
    val offered: Boolean,
    /**
     * True once everything it stands for is on the device and nothing is still on its way: a film or an
     * episode, or every episode of a show or a season. A show is not downloaded because one episode is.
     */
    val downloaded: Boolean,
    /** Some episodes of a show or a season are on the device, not all of them. */
    val partly: Boolean = false,
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
    /** Why this version, for something proposed: one short sentence, or nothing when there was no choice to make. */
    val note: String? = null,
)

@Immutable
data class OfflineModel(
    /** The work being reviewed before it is downloaded, or whose copies are being looked after; null for the general sheet. */
    val reviewing: MediaId? = null,
    /** What is being reviewed, in a line ("Severance · Season 1"); null for the general sheet. */
    val subject: String? = null,
    /** What Reflux would keep if asked now; null when there is nothing to propose. */
    val proposal: OfflinePlan?,
    val proposed: List<OfflineLine>,
    /** What was considered and left out, each as a sentence. */
    val leftOut: List<String>,
    /** "2.1 GB needed · room for 12 GB", for the proposal. */
    val room: String?,
    /** The copies of the reviewed work; none for the general sheet (they are on the Downloads page). */
    val downloads: List<OfflineLine>,
)

/** A work's title as a line says it: "Heat", or "Breaking Bad" with "S1 · E2 · Cat's in the Bag..." beneath. */
private fun Library.lineTitle(itemId: MediaId): Pair<String, String?> = item(itemId)?.let { heading(it) } ?: ("No longer in your library" to null)

private fun episodes(count: Int) = if (count == 1) "1 episode" else "$count episodes"

internal fun Library.offlineStatus(itemId: MediaId, offline: Offline): OfflineStatus {
    val item = item(itemId)
    val downloads = downloadsOf(itemId)
    val waiting = downloads.firstOrNull { it.state == DownloadState.DOWNLOADING } ?: downloads.firstOrNull { it.active } ?: downloads.firstOrNull { it.state == DownloadState.PAUSED }
    val stopped = downloads.firstOrNull { it.state == DownloadState.FAILED || it.state == DownloadState.UNAVAILABLE }
    if (item is Show || item is Season) {
        // A show or a season is the episodes it is made of, counted one by one.
        val coverage = offlineCoverage(itemId)
        val all = coverage.works
        return OfflineStatus(
            // A show whose every episode is a file on this device has nothing to gain from copies.
            offered = downloads.isNotEmpty() || (offline.possible() && all > 0 && episodesOffDevice(if (item is Season) item.showId else itemId).isNotEmpty()),
            downloaded = coverage.complete && waiting == null,
            partly = coverage.kept in 1 until all,
            busy = waiting != null,
            failed = waiting == null && stopped != null,
            label = when {
                waiting != null -> listOfNotNull(
                    if (coverage.pending > 1) "Downloading ${episodes(coverage.pending)}" else Formatting.downloadState(waiting),
                    "${coverage.kept} of $all on this device".takeIf { coverage.kept > 0 },
                ).joinToString("  ·  ")
                stopped != null -> if (coverage.failed > 1) "${episodes(coverage.failed)} didn't finish" else "An episode didn't finish downloading"
                coverage.complete -> if (all == 1) "On this device" else "All ${episodes(all)} on this device"
                coverage.kept > 0 -> "${coverage.kept} of ${episodes(all)} on this device"
                else -> null
            },
            progress = waiting?.progress,
        )
    }
    val completed = downloads.count { it.state == DownloadState.COMPLETED }
    // A film or an episode that is on the device as a file of the user's own has nothing to gain from a copy.
    val alreadyHere = downloads.isEmpty() && (item is Movie || item is Episode) &&
        versions(itemId).any { it.locality == SourceLocality.DEVICE && it.availability != Availability.UNAVAILABLE }
    return OfflineStatus(
        offered = downloads.isNotEmpty() || (offline.possible() && !alreadyHere),
        downloaded = completed > 0 && waiting == null && stopped == null,
        busy = waiting != null,
        failed = waiting == null && stopped != null,
        label = when {
            waiting != null -> Formatting.downloadState(waiting)
            stopped != null -> "Download didn't finish"
            completed > 0 -> "On this device"
            else -> null
        },
        progress = waiting?.progress,
    )
}

/** Where one episode stands with being kept, for the mark beside its row; [state] null when nothing of it is. */
@Immutable
data class EpisodeOffline(val state: DownloadState?, val progress: Float?)

/**
 * For each episode of [showId] that can be kept or has been, where it stands, by the download that says most
 * about it. An episode that is only a file on this device has nothing to keep and is not in it.
 */
internal fun Library.episodeOffline(showId: MediaId, offline: Offline): Map<MediaId, EpisodeOffline> {
    val copies = downloadsOf(showId).groupBy { it.itemId }.mapValues { (_, copies) ->
        val telling = copies.firstOrNull { it.state == DownloadState.COMPLETED }
            ?: copies.firstOrNull { it.state == DownloadState.DOWNLOADING }
            ?: copies.firstOrNull { it.active }
            ?: copies.firstOrNull { it.state == DownloadState.PAUSED }
            ?: copies.first()
        EpisodeOffline(telling.state, telling.progress)
    }
    val keepable = if (offline.possible()) episodesOffDevice(showId).associateWith { EpisodeOffline(null, null) } else emptyMap()
    return keepable + copies
}

private fun Library.line(download: Download): OfflineLine {
    val (title, caption) = lineTitle(download.itemId)
    return OfflineLine(download.id, title, caption, Formatting.downloadDetail(download), download.state, download.progress.takeIf { download.state != DownloadState.COMPLETED })
}

internal fun Library.offlineModel(offline: Offline, reviewing: MediaId? = null): OfflineModel {
    val plan = if (reviewing != null) offline.planFor(listOf(reviewing)) else offline.suggest()
    val reviewed = reviewing?.let(::item)
    // A show or a season is reviewed episode by episode: what is left out is said of each.
    val collection = reviewed is Show || reviewed is Season
    val names = mutableMapOf<SourceId, String?>()
    // Every copy is on the Downloads page; the sheet lists those of the work it is about.
    val copies = if (reviewing != null) downloadsOf(reviewing) else emptyList()
    return OfflineModel(
        reviewing = reviewing,
        subject = when (reviewed) {
            null -> null
            is Season -> listOfNotNull(item(reviewed.showId)?.title, reviewed.title).joinToString("  ·  ")
            else -> lineTitle(reviewed.id).let { (title, caption) -> listOfNotNull(title, caption).joinToString("  ·  ") }
        },
        proposal = plan.takeUnless { it.empty },
        proposed = plan.downloads.map { planned ->
            val (title, caption) = lineTitle(planned.itemId)
            val why = when (planned.reason) {
                OfflineReason.NEXT_EPISODE -> "Next episode"
                OfflineReason.IN_PROGRESS -> "Continue watching"
                OfflineReason.CHOSEN -> null
            }
            val from = names.getOrPut(planned.version.location.sourceId) { source(planned.version.location.sourceId)?.displayName }
            OfflineLine(
                id = planned.itemId.value,
                // Within a season or a show the show's name goes without saying; the episode is the line.
                title = if (collection && caption != null) caption else title,
                caption = if (collection) why else listOfNotNull(caption, why).joinToString("  ·  ").ifEmpty { null },
                detail = (Formatting.offlineVersion(planned.version) + ((if (planned.sizeKnown) "" else "about ") + Formatting.size(planned.bytes)) + listOfNotNull(from?.let { "from $it" }))
                    .joinToString("  ·  "),
                // A pin set aside because its version cannot be had now is said, as Play says it; the pin stays.
                note = pinnedVersions(planned.itemId).takeIf { it.isNotEmpty() && planned.version.id !in it }?.let { Problems.OFFLINE_PIN_SET_ASIDE }
                    ?: Formatting.offlineChoice(planned.choice),
            )
        },
        leftOut = when {
            // Asked about one thing, every reason it is not being downloaded is worth saying in full, except that it
            // is here or on its way, which its copy below says already.
            reviewing != null && !collection -> plan.skipped
                .filterNot { copies.isNotEmpty() && (it.reason == OfflineSkipReason.ALREADY_ON_DEVICE || it.reason == OfflineSkipReason.ALREADY_DOWNLOADING) }
                .map { Problems.offlineSkip(it.reason) }.distinct()
            // Otherwise only what the user could not have guessed, of each episode or work.
            else -> plan.skipped.filter { it.reason != OfflineSkipReason.ALREADY_ON_DEVICE && it.reason != OfflineSkipReason.ALREADY_DOWNLOADING }.map { skip ->
                val (title, caption) = lineTitle(skip.itemId)
                (if (collection && caption != null) caption else listOfNotNull(title, caption).joinToString(" · ")) + ": " + Problems.offlineSkipShort(skip.reason)
            }
        },
        room = plan.takeUnless { it.empty }?.let { "${Formatting.size(it.requiredBytes)} needed  ·  ${Formatting.size(it.usableBytes)} available" },
        downloads = copies.sortedBy { it.createdAtEpochMs }.map { line(it) }
            .let { lines -> if (collection) lines.map { it.copy(title = it.caption ?: it.title, caption = null) } else lines },
    )
}

/** One copy on the Downloads page: what it is a copy of, which version, and where it stands. */
@Immutable
data class DownloadItem(
    val id: String,
    /** The work it is a copy of; selecting the line opens its page (an episode's own page for an episode). */
    val itemId: MediaId,
    val title: String,
    val caption: String?,
    /** The version kept, in a viewer's words: "1080p · HDR10 · 4.3 GB · from Living Room". */
    val version: String,
    val state: DownloadState,
    /** Where it stands, in a few words ("Downloading · 45%", "Waiting for its source to come back"). */
    val status: String,
    val progress: Float?,
    val poster: ArtworkLocator?,
)

/** Every copy there is, grouped by what the user may want to do about it. Not a second library: only what is kept. */
@Immutable
data class DownloadsModel(
    /** Waiting, under way, or paused, in the order they will be made. */
    val active: List<DownloadItem>,
    /** Stopped without finishing, or no longer on the device. */
    val stopped: List<DownloadItem>,
    /** On the device, by work: a show's episodes together and in order. */
    val completed: List<DownloadItem>,
    /** "3 on this device · 9.1 GB · 41 GB available". */
    val summary: String,
) {
    val empty: Boolean get() = active.isEmpty() && stopped.isEmpty() && completed.isEmpty()
}

internal fun Library.downloadsModel(offline: Offline): DownloadsModel {
    val all = downloads()
    val cards = cards(all.map { it.itemId }.distinct().mapNotNull(::entry)).associateBy { it.id }
    val names = mutableMapOf<SourceId, String?>()
    fun item(download: Download): DownloadItem {
        val (title, caption) = lineTitle(download.itemId)
        // The version as it was copied: the copy itself once it is a version, its origin until then.
        val version: MediaVersion? = copyOf(download)?.version ?: version(download.originVersionId)?.version
        val from = names.getOrPut(download.origin.sourceId) { source(download.origin.sourceId)?.displayName }
        val size = (download.totalBytes ?: version?.sizeBytes?.takeIf { it > 0 })?.let { Formatting.size(it) }
        return DownloadItem(
            id = download.id,
            itemId = download.itemId,
            title = title,
            caption = caption,
            version = (version?.let { Formatting.offlineVersion(it) }.orEmpty() + listOfNotNull(size, from?.let { "from $it" })).joinToString("  ·  "),
            state = download.state,
            status = when (download.state) {
                DownloadState.DOWNLOADING -> listOfNotNull(Formatting.downloadState(download), download.totalBytes?.let { "${Formatting.size(download.bytesDownloaded)} of ${Formatting.size(it)}" }).joinToString("  ·  ")
                else -> Formatting.downloadState(download)
            },
            // How far it got is worth showing while it is on its way, or paused; not once it stopped or finished.
            progress = download.progress.takeIf { download.active || download.state == DownloadState.PAUSED },
            poster = cards[download.itemId]?.let { it.poster ?: it.wide },
        )
    }
    // What is kept reads as the library does: by work, and a show's episodes in their order.
    val order = compareBy<Download>({ lineTitle(it.itemId).first.lowercase() }, { (item(it.itemId) as? Episode)?.seasonNumber ?: 0 }, { (item(it.itemId) as? Episode)?.let { episode -> episode.episodeNumber ?: episode.absoluteNumber } ?: 0 })
    val completed = all.filter { it.state == DownloadState.COMPLETED }
    return DownloadsModel(
        active = all.filter { it.active || it.state == DownloadState.PAUSED }.map(::item),
        stopped = all.filter { it.state == DownloadState.FAILED || it.state == DownloadState.UNAVAILABLE }.map(::item),
        completed = completed.sortedWith(order).map(::item),
        summary = listOfNotNull(
            "${completed.size} on this device",
            completed.takeIf { it.isNotEmpty() }?.let { kept -> Formatting.size(kept.sumOf { it.totalBytes ?: it.bytesDownloaded }) },
            "${Formatting.size(offline.usableBytes())} available",
        ).joinToString("  ·  "),
    )
}

/** A version in the words of someone keeping it: how it looks, never what it is encoded with. */
internal fun Formatting.offlineVersion(version: MediaVersion): List<String> = listOfNotNull(
    resolution(version.stream.video?.nominalHeight),
    version.stream.video?.dynamicRange?.let(::dynamicRange),
    version.edition,
)

/** Why a version was the one to keep, said briefly; nothing when there was only one to keep. */
internal fun Formatting.offlineChoice(choice: OfflineChoice): String? = when (choice) {
    OfflineChoice.ONLY_VERSION -> null
    OfflineChoice.BEST_HERE -> "Chosen because it plays best on this device."
    OfflineChoice.SMALLER_EQUIVALENT -> "Chosen because it looks the same here in less space."
    OfflineChoice.MADE_TO_FIT -> "The best version that fits in the space on this device."
    OfflineChoice.PINNED -> "The version you chose for this."
}

internal fun Formatting.downloadState(download: Download): String = when (download.state) {
    DownloadState.QUEUED -> "Waiting to download"
    DownloadState.PREPARING -> "Preparing"
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
    OfflineSkipReason.SOURCE_UNAVAILABLE -> "This can't be downloaded right now. Its source isn't connected."
    OfflineSkipReason.NOT_DOWNLOADABLE -> "Its source doesn't allow keeping a copy."
    OfflineSkipReason.NOT_PLAYABLE_HERE -> "No version of this plays on this device."
}

internal fun Problems.offlineSkipShort(reason: OfflineSkipReason): String = when (reason) {
    OfflineSkipReason.ALREADY_ON_DEVICE -> "already on this device"
    OfflineSkipReason.ALREADY_DOWNLOADING -> "already downloading"
    OfflineSkipReason.NOT_ENOUGH_STORAGE -> "not enough room"
    OfflineSkipReason.SOURCE_UNAVAILABLE -> "its source isn't connected"
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
