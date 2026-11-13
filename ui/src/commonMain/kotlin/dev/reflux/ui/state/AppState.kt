package dev.reflux.ui.state

import dev.reflux.library.MetadataRefresh
import dev.reflux.library.onlineMetadata
import dev.reflux.library.setOnlineMetadata
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputModalityTracker
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.InputState
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.ScoredCandidate
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.VersionId
import dev.reflux.core.source.MediaSource
import dev.reflux.library.Library
import dev.reflux.core.diagnostics.SupportReport
import dev.reflux.core.playback.PlaybackReport
import dev.reflux.library.LibraryEntry
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.LibraryScope
import dev.reflux.library.setLibraryIncluded
import dev.reflux.library.setSourceIncluded
import dev.reflux.library.Offline
import dev.reflux.library.PipelineActivity
import dev.reflux.library.pinVersion
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.library.planPlayback
import dev.reflux.library.setWatchedEverywhere
import dev.reflux.core.model.ProfileId
import dev.reflux.core.profile.PinCheck
import dev.reflux.core.profile.PinPolicy
import dev.reflux.library.Profile
import dev.reflux.library.accountSourcesOf
import dev.reflux.library.checkPin
import dev.reflux.library.clearWatchHistory
import dev.reflux.library.createProfile
import dev.reflux.library.deleteProfile
import dev.reflux.library.profileDownloadIds
import dev.reflux.library.profiles
import dev.reflux.library.removePin
import dev.reflux.library.renameProfile
import dev.reflux.library.setPin
import dev.reflux.library.switchProfile
import dev.reflux.library.resetProfile
import dev.reflux.library.keepEndpoints
import dev.reflux.library.endpoints
import dev.reflux.library.seenByActiveProfile
import dev.reflux.library.privateWords
import dev.reflux.library.visibleSources
import dev.reflux.library.currentTime
import kotlinx.coroutines.CancellationException
import dev.reflux.core.metadata.ProviderAttribution
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The state of the Reflux experience: where the user is, what is playing, and how they are interacting.
 *
 * It talks to the Media Core only: nothing here knows which sources exist or which engine plays. Platform
 * shells create one instance, feed it input, and render it with `RefluxApp` and `PlayerOverlay`.
 *
 * @param scope runs on the UI thread.
 * @param background where library reads and writes run, off the UI thread.
 */
@Stable
class AppState(
    val library: Library,
    private val pipeline: LibraryPipeline,
    internal val sources: (SourceId) -> MediaSource?,
    val platform: Platform,
    internal val scope: CoroutineScope,
    internal val background: CoroutineDispatcher = Dispatchers.Default,
) {
    val navigator = Navigator()

    /** Advances whenever library content or user state may have changed; screens reload when it does. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Advances whenever a download changes state or makes visible progress. Only what shows downloads reloads
     * for it ([offlineState]); a finished or removed copy changes the library, and so [revision], as well.
     */
    var downloadRevision by mutableIntStateOf(0)
        private set

    /** What is playing, if anything. Rendered above everything else by the shell. */
    var playback: PlaybackSession? by mutableStateOf(null)
        private set

    /** The session that has the screen: the full player. Null while nothing plays, or it plays in the mini-player. */
    val watching: PlaybackSession? get() = playback?.takeIf { it.presentation == PlayerPresentation.FULL }

    /** The session playing in the mini-player while the viewer browses, if one is. */
    val docked: PlaybackSession? get() = playback?.takeIf { it.presentation == PlayerPresentation.MINI }

    /**
     * Whether what is playing can step aside into a mini-player here: the shell can show its video surface in the
     * dock, and the device is not a television, whose player is the whole screen (docs/NAVIGATION.md, section 8).
     */
    val miniPlayerAvailable: Boolean get() = platform.docksVideo && !platform.television

    /** Whether what is playing can go on in the system's own window when Reflux is left ([Platform.systemPresentation]). */
    val systemPresentationAvailable: Boolean get() = platform.systemPresentation && !platform.television

    /**
     * How far a back gesture has gone (0 to 1) toward collapsing the full player into the mini-player, while one is in
     * progress. Only what is drawn follows it: the session changes when the gesture completes, so cancelling it leaves
     * nothing to restore (docs/planning/M10-PLAYER-SPEC.md, section 4.4).
     */
    var backPreview: Float? by mutableStateOf(null)

    /** The system is animating the window into its picture-in-picture (Android 15 and later): the player's controls have already left. Set by the shell. */
    var systemTransition: Boolean by mutableStateOf(false)

    /** Where the mini-player's picture is in the window, as its dock last laid it out; null while there is no dock. */
    internal var dockSlot: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)

    /** Whether something covers the page, and with it the dock: a sheet, a picker, the question of who is watching. */
    private val pageCovered: Boolean get() = offlineOpen || folderPicker != null || profileGate != null

    /**
     * Where the shell's video surface goes now (see [VideoFrame]). Until the dock has been laid out the surface stays
     * where it is, so it moves once, to where it belongs.
     */
    val videoFrame: VideoFrame
        get() = when {
            docked == null -> VideoFrame.Full
            pageCovered -> VideoFrame.Hidden
            // A dead picture is not left showing in the dock: the dock says what happened.
            docked?.error != null -> VideoFrame.Hidden
            else -> dockSlot?.let { VideoFrame.Docked(it) } ?: VideoFrame.Full
        }

    private var offlineShown by mutableStateOf(false)

    /** Whether the sheet about what is kept on the device is open. */
    var offlineOpen: Boolean
        get() = offlineShown
        set(value) {
            offlineShown = value
            if (!value) {
                offlineReview = null
                offlineScope = LibraryScope.Unified
            }
        }

    /** The work the offline sheet is proposing to keep, while it is reviewing one before it is downloaded. */
    var offlineReview: MediaId? by mutableStateOf(null)
        private set

    /** Where the work the sheet reviews was opened from: a source's page keeps that source's copy. */
    var offlineScope: LibraryScope by mutableStateOf(LibraryScope.Unified)
        private set

    /** Sources being checked or scanned because the user asked, so that their rows can say so. */
    var checking: Set<SourceId> by mutableStateOf(emptySet())
        private set

    /** The folder picker Reflux draws itself, while it is open (see [Platform.folderBrowser]). */
    var folderPicker: FolderPicker? by mutableStateOf(null)
        private set

    var searchQuery by mutableStateOf("")

    /** A short, transient message ("Its source isn't connected"). */
    var notice: String? by mutableStateOf(null)
        private set

    /** What the background pipeline is doing, for a quiet progress indicator. */
    val activity: StateFlow<PipelineActivity> get() = pipeline.activity

    /** Whether titles and artwork could not be fetched last time; the library works without them. */
    val metadataUnreachable: StateFlow<Boolean> get() = pipeline.metadataUnreachable

    /** How the metadata provider in use asks to be credited, for the About page; null without one. */
    val metadataAttribution: ProviderAttribution? get() = pipeline.metadataAttribution

    /**
     * Media Atmosphere (docs/DESIGN.md): whether artwork may color the space around it. Off by default, so the
     * canonical page is neutral; the viewer's choice is kept with the library.
     */
    var mediaAtmosphere by mutableStateOf(false)
        private set

    /**
     * Whether Reflux may look up titles, descriptions, and artwork online (docs/METADATA.md, "Online lookups"). On
     * unless the viewer turned it off; off, what sources say and what was fetched before is all there is.
     */
    var onlineMetadata by mutableStateOf(true)
        private set

    /** Whether the build has a metadata provider to look things up in, which the switch above is about. */
    val hasMetadataProvider: Boolean get() = pipeline.hasProvider

    init {
        scope.launch { runCatching { read { setting(MEDIA_ATMOSPHERE) } }.getOrNull()?.let { mediaAtmosphere = it == "on" } }
        scope.launch { runCatching { read { this.onlineMetadata } }.getOrNull()?.let { onlineMetadata = it } }
        // Who is watching is known, or asked, from the moment Reflux opens (docs/WATCH_STATE.md, "Profiles").
        scope.launch { askWhoIsWatchingAtStart() }
    }

    fun setOnlineMetadata(on: Boolean): Job {
        onlineMetadata = on
        return attempt("That couldn't be saved. Try again.") {
            read { this.setOnlineMetadata(on) }
            // Turned on, what was waiting for it is looked up now.
            if (on) refresh()
            invalidate()
        }
    }

    /**
     * Reads a work's details and artwork again (Identify's "Refresh details"): only when asked, and keeping
     * everything known when the catalog cannot be reached. Says what came of it.
     */
    fun refreshDetails(itemId: MediaId): Job = attempt("Details couldn't be refreshed. Try again.") {
        val result = withContext(background) { pipeline.refreshDetails(itemId) }
        notify(
            when (result) {
                MetadataRefresh.REFRESHED -> "Details and artwork refreshed"
                MetadataRefresh.UNREACHABLE -> "The catalog couldn't be reached. Nothing was changed."
                MetadataRefresh.NOT_FOUND -> "The catalog has nothing more for this."
                null -> "Online lookups are off."
            },
        )
        invalidate()
    }

    fun setMediaAtmosphere(on: Boolean): Job {
        mediaAtmosphere = on
        return attempt("That couldn't be saved. Try again.") { read { setSetting(MEDIA_ATMOSPHERE, if (on) "on" else null) } }
    }

    /** Volume and mute carry over from one playback session to the next. */
    internal var volume = 100
    internal var muted = false

    private var starting = false
    private var releasing: Job? = null
    private var choosingFolder = false
    private var noticeJob: Job? = null
    private var watchJob: Job? = null

    /** Starts keeping the library current: an initial refresh, then change and reconnect watching. */
    fun start() {
        scope.launch { library.changes().collect { revision++ } }
        // A download's progress is something the places that show downloads follow: often enough to watch it
        // move, not so often that they reload for every megabyte, and without reloading every other screen.
        platform.offline?.let { offline ->
            scope.launch {
                offline.changes.collect {
                    downloadRevision++
                    delay(DOWNLOAD_PROGRESS_MS)
                }
            }
        }
        refresh()
        watchSources()
    }

    private fun watchSources() {
        watchJob?.cancel()
        watchJob = pipeline.watch(scope + background)
    }

    /**
     * Runs [block] on the library, off the UI thread. A read that fails after its caller has gone away (the page was
     * left, the window closed) ends as the cancellation it is: the failure is no one's to report, and reported, it
     * would reach the window as an unhandled error. A library read cannot be interrupted, so it can still fail after
     * the caller was cancelled, which coroutines would otherwise surface instead of the cancellation.
     */
    internal suspend fun <T> read(block: suspend Library.() -> T): T = try {
        withContext(background) { library.block() }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        throw e
    }

    /** Tells every screen that user state changed. */
    fun invalidate() {
        revision++
    }

    /**
     * Runs something the user asked for. If it fails, the user is told [failure] and the cause is reported,
     * so no action can end the application or fail without a word.
     */
    internal fun attempt(failure: String, action: suspend () -> Unit): Job = scope.launch {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            platform.report(failure, e)
            notify(failure)
        }
    }

    // Identify -----------------------------------------------------------------------------------

    /** Whether works can be identified here: the build includes a metadata provider to search. */
    val canIdentify: Boolean get() = pipeline.canIdentify

    internal suspend fun identifyCandidates(itemId: MediaId, title: String, year: Int?): List<ScoredCandidate> =
        withContext(background) { pipeline.identifyCandidates(itemId, title, year) }

    internal suspend fun identify(itemId: MediaId, candidate: MetadataCandidate): MediaId =
        withContext(background) { pipeline.identify(itemId, candidate) }

    internal suspend fun undoIdentification(itemId: MediaId): MediaId? =
        withContext(background) { pipeline.undoIdentification(itemId) }

    /** After Identify: the work, as it is now, opens in place of the pages that led there. */
    internal fun identified(from: MediaId, to: MediaId?) {
        navigator.leave { it is Destination.Identify || (it is Destination.Details && it.itemId == from) }
        to?.let { navigator.open(Destination.Details(it)) }
        invalidate()
    }

    // Library actions ----------------------------------------------------------------------------

    fun refresh(sourceId: SourceId? = null): Job = scope.launch {
        try {
            withContext(background) { pipeline.refresh(sourceId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            platform.report("Refreshing the library failed", e)
            notify(Problems.LIBRARY_NOT_UPDATED)
        }
        // A source that is back is a reason to try what was waiting for it.
        platform.offline?.let { offline -> withContext(background) { offline.retryWaiting() } }
        invalidate()
    }

    /**
     * Asks whether a source is there, and nothing more: after playback from it stopped, its state is a
     * question of its own. Unlike [checkSource] it does not read the source again: the playback that failed
     * is still open, and only playback stopping or finishing ends its session on a media server (a rescan
     * brings positions up to date with `updatePosition`, which leaves that session open).
     */
    fun checkAvailability(id: SourceId): Job = scope.launch {
        val source = sources(id) ?: return@launch
        if (id in checking) return@launch
        checking = checking + id
        try {
            withContext(background) { library.refreshAvailability(source) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            platform.report("Checking a source failed", e)
        } finally {
            checking = checking - id
        }
        invalidate()
    }

    /** Checks a source again and rescans it: "Refresh" on a source that is there, "Try again" on one that is not. */
    fun checkSource(id: SourceId): Job = scope.launch {
        if (id in checking) return@launch
        checking = checking + id
        try {
            refresh(id).join()
        } finally {
            checking = checking - id
        }
    }

    // Navigation ---------------------------------------------------------------------------------

    /** Whether this platform keeps copies, and so has a Downloads destination. */
    val keepsCopies: Boolean get() = platform.offline != null

    /** The global destinations this platform has, in the navigation's order: the primary ones, then the utility area. */
    val destinations: List<Destination.Section> get() = Destinations.filter { it != Destination.Downloads || keepsCopies }

    /** The utility area this platform has (docs/NAVIGATION.md, section 1), in its order. */
    val utilityDestinations: List<Destination.Section> get() = UtilityDestinations.filter { it in destinations }

    /** Goes to a global destination, or back to its top when it is the one already shown. */
    fun go(section: Destination.Section) {
        if (section !in destinations) return
        navigator.show(section)
    }

    /** The next or previous global destination, from the one the viewer is in; the ends do not wrap. */
    private fun step(by: Int): Boolean {
        val all = destinations
        val here = all.indexOf(navigator.section)
        val next = all.getOrNull(here + by) ?: return false
        go(next)
        return true
    }

    /**
     * Moves focus to the navigation's current destination, where a television's Back goes first (docs/NAVIGATION.md,
     * section 2). Installed by the root composable; returns false when focus is already there or cannot move.
     */
    internal var focusNavigation: (() -> Boolean)? = null

    // Sources ------------------------------------------------------------------------------------

    /** Opens Sources, or leaves it for Home when it is the destination already shown. */
    fun toggleSources() {
        if (navigator.section == Destination.Sources) navigator.show(Destination.Home) else go(Destination.Sources)
    }

    /**
     * Includes a source in the unified library, or leaves it out (docs/NAVIGATION.md, section 5). Nothing else about
     * it changes: it stays connected and can be browsed and played from its own page.
     */
    fun setSourceIncluded(id: SourceId, included: Boolean, name: String): Job = attempt("That couldn't be changed. Try again.") {
        read { setSourceIncluded(id, included) }
        notify(if (included) "$name is in your library." else "$name is no longer in your library. You can still open it from Sources.")
        invalidate()
    }

    /** One library of a source in the unified library, or not; it counts while its source is included. */
    fun setLibraryIncluded(id: SourceId, section: String, included: Boolean, name: String): Job = attempt("That couldn't be changed. Try again.") {
        read { setLibraryIncluded(id, section, included) }
        notify(if (included) "$name is in your library." else "$name is no longer in your library.")
        invalidate()
    }

    /**
     * A source was connected or signed in again: the flow that did it is left, the user is told, and the
     * library catches up by itself.
     */
    internal fun sourceConnected(id: SourceId, name: String, updated: Boolean) {
        navigator.leave(::partOfAdding)
        notify(if (updated) "$name is connected again." else "$name is connected. Your library is being updated.")
        invalidate()
        watchSources()
        refresh(id)
    }

    private fun partOfAdding(destination: Destination) = destination is Destination.AddSource || destination is Destination.Connect

    // Offline ------------------------------------------------------------------------------------

    /** Opens the offline sheet on what Reflux would keep of [itemId], for the user to confirm. */
    fun reviewDownload(itemId: MediaId, scope: LibraryScope = LibraryScope.Unified) {
        offlineOpen = true
        offlineReview = itemId
        offlineScope = scope
    }

    /**
     * Keeps [itemId] for watching without its source: a film or an episode itself, every episode of a season,
     * the next few episodes of a show. What of it was paused or did not finish is picked up again. Which version
     * is Reflux's to work out; the user is told that it is happening, or why not.
     */
    fun download(itemId: MediaId, scope: LibraryScope = LibraryScope.Unified): Job = attempt(Problems.OFFLINE_NOT_PREPARED) {
        val offline = platform.offline ?: return@attempt
        val (plan, resumed) = withContext(background) {
            val plan = offline.planFor(listOf(itemId), scope).also(offline::enqueue)
            plan to offline.resumeStopped(listOf(itemId), scope)
        }
        notify(
            when {
                plan.downloads.isEmpty() && resumed > 0 -> Problems.OFFLINE_RESUMED
                // What was left out of a season is said where its episodes are listed; the rest is on its way.
                plan.downloads.isEmpty() -> plan.skipped.firstOrNull()?.let { Problems.offlineSkip(it.reason) } ?: Problems.NOTHING_TO_PLAY
                plan.downloads.any { it.madeToFit } -> Problems.OFFLINE_PREPARING_TO_FIT
                else -> Problems.OFFLINE_PREPARING
            },
        )
        invalidate()
    }

    /** Accepts what the offline sheet proposes. */
    fun prepareOffline(): Job = attempt(Problems.OFFLINE_NOT_PREPARED) {
        val offline = platform.offline ?: return@attempt
        val plan = withContext(background) { offline.suggest().also(offline::enqueue) }
        if (plan.downloads.isNotEmpty()) notify(if (plan.downloads.any { it.madeToFit }) Problems.OFFLINE_PREPARING_TO_FIT else Problems.OFFLINE_PREPARING)
        invalidate()
    }

    fun pauseDownload(id: String): Job = changeDownload { it.pause(id) }

    fun resumeDownload(id: String): Job = changeDownload { it.resume(id) }

    /** Removes a download and its copy from the device. The work and what was watched of it stay. */
    fun removeDownload(id: String): Job = changeDownload { it.remove(id) }

    /** Removes several downloads at once (every episode of a season kept on the device). */
    fun removeDownloads(ids: List<String>): Job = changeDownload { offline -> ids.forEach { offline.remove(it) } }

    /** Opens Downloads, or leaves it for Home when it is the destination already shown. */
    fun toggleDownloads() {
        if (navigator.section == Destination.Downloads) navigator.show(Destination.Home) else go(Destination.Downloads)
    }

    private fun changeDownload(change: suspend (Offline) -> Unit): Job = attempt(Problems.DOWNLOAD_NOT_CHANGED) {
        val offline = platform.offline ?: return@attempt
        withContext(background) { change(offline) }
        invalidate()
    }

    /** Asks for a media folder and scans it. Everything after the choice is automatic. */
    fun addFolder(): Job = attempt(Problems.FOLDER_NOT_ADDED) {
        if (choosingFolder || folderPicker != null) return@attempt
        platform.folderBrowser?.let { browser ->
            // No picker of the platform's to hand over to: Reflux shows its own, over the same storage.
            choosingFolder = true
            val roots = try {
                browser.roots()
            } finally {
                choosingFolder = false
            } ?: return@attempt notify(Problems.STORAGE_NOT_ALLOWED)
            folderPicker = FolderPicker(this, browser, roots)
            return@attempt
        }
        choosingFolder = true
        val choice = try {
            platform.addMediaFolder()
        } finally {
            choosingFolder = false
        }
        folderChosen(choice)
    }

    internal fun closeFolderPicker() {
        folderPicker = null
    }

    internal suspend fun folderChosen(choice: FolderChoice) {
        Problems.folder(choice)?.let(::notify)
        when (choice) {
            is FolderChoice.Added -> {
                navigator.leave(::partOfAdding)
                invalidate()
                watchSources()
                refresh(choice.sourceId).join()
            }
            // Choosing it again is a fair way to ask for a rescan.
            is FolderChoice.AlreadyInLibrary -> refresh(choice.sourceId).join()
            FolderChoice.Cancelled, is FolderChoice.Unreadable, is FolderChoice.NotAFolder -> Unit
        }
    }

    /**
     * What plays from [id] stops, and its place is recorded, before that source is taken away: with a mini-player the
     * viewer can be in Sources while something plays.
     */
    private suspend fun stopPlayingFrom(id: SourceId) {
        if (playback?.playingFrom == id) finishPlayback()
    }

    fun removeSource(id: SourceId): Job = attempt("That source couldn't be removed.") {
        stopPlayingFrom(id)
        platform.removeSource(id)
        watchSources()
        invalidate()
    }

    fun toggleFavorite(entry: LibraryEntry): Job = attempt("Your favorites couldn't be updated.") {
        read { setFavorite(entry.item.id, !entry.favorite) }
        invalidate()
    }

    /**
     * Makes [versionId] the version Play always uses for [itemId] ("Always play this"), or with null lets Best
     * Version choose again ("Let Reflux choose").
     */
    fun pinVersion(itemId: MediaId, versionId: VersionId?): Job = attempt("That choice couldn't be saved.") {
        read { pinVersion(itemId, versionId) }
        invalidate()
        notify(if (versionId != null) "Play will always use this version." else "Play will choose the best version for this device.")
    }

    /** Marks a work watched or unwatched here and on every source that keeps its own watch state. */
    fun setWatched(itemId: MediaId, watched: Boolean): Job = attempt("That couldn't be marked as ${if (watched) "watched" else "unwatched"}.") {
        read { setWatchedEverywhere(itemId, watched, sources) }
        invalidate()
    }

    // Playback -----------------------------------------------------------------------------------

    /**
     * Plays a work: a movie or episode directly, a show from its Next Up episode (or its first).
     *
     * @param versionId a specific version instead of Best Version.
     * @param resume false starts from the beginning.
     * @param scope where it is played from: a source's page plays that source's copy (docs/NAVIGATION.md, section 4).
     */
    fun play(itemId: MediaId, versionId: VersionId? = null, resume: Boolean = true, scope: LibraryScope = LibraryScope.Unified): Job = attempt("This couldn't be played right now.") {
        // The full player is in the way of everything, so nothing is started from under it. What plays in the
        // mini-player gives way to what the viewer chose next: one session, never two.
        if (watching != null || starting) return@attempt
        starting = true
        try {
            // One engine and one video surface at a time: the player that was just closed is gone first.
            awaitRelease()
            val playable = read { playableFor(itemId, scope) } ?: return@attempt notify(Problems.NOTHING_TO_PLAY)
            // Play on what is already playing beside the page is the way back to it, not a second start.
            docked?.takeIf { versionId == null && resume && it.entry?.item?.id == playable }?.let { return@attempt it.expand() }
            val plan = read { planPlayback(playable, platform.device, sources, versionId, resume, preferences = viewer, scope = scope) }
                ?: return@attempt notify(read { unplayableReason(playable, platform.device, sources, viewer, scope) })
            if (!platform.canPlay) return@attempt notify(Problems.noEngine(platform.playbackSetupHint))
            // Only now that there is something to play instead: what was in the mini-player ends, its place recorded.
            if (playback != null) finishPlayback()
            val player = platform.createPlayer() ?: run {
                platform.report("The playback engine is installed but could not be started")
                return@attempt notify(Problems.PLAYBACK_DID_NOT_START)
            }
            val session = PlaybackSession(this@AppState, player, scope)
            try {
                session.load(plan)
            } catch (e: Exception) {
                session.close()
                throw e
            }
            playback = session
        } finally {
            starting = false
        }
    }

    /**
     * Continues a work chosen outside Reflux (the launcher's Watch Next row): what was playing stops, the
     * work's details page opens, and it plays as Play there would, from the work's own place. Should it not
     * play (its source is away), the details page is where the user is left, with the reason.
     */
    fun continueFromOutside(itemId: MediaId, profile: ProfileId? = null): Job {
        // A card says who is watching: the question asked at start is not asked over it.
        continuedFromOutside = true
        return continueFromOutsideIn(itemId, profile)
    }

    private fun continueFromOutsideIn(itemId: MediaId, profile: ProfileId?): Job = scope.launch {
        // A card opens in the profile whose Continue Watching it was, without asking who is watching; a profile that
        // has a PIN now is opened with it first (docs/WATCH_STATE.md, "Profiles").
        loadProfiles()
        val target = profile?.let { id -> profiles.firstOrNull { it.id == id } } ?: activeProfile
        when {
            target == null -> Unit
            target.hasPin && !(target.active && unlocked) -> return@launch run { profileGate = ProfileGate.Unlock(target, required = true, then = itemId) }
            !target.active -> becomeProfile(target)
            else -> profileGate = null
        }
        // Only what this profile can see is opened: a link naming anything else shows nothing of it.
        if (!read { seenByActiveProfile(itemId) }) return@launch notify(Problems.NOTHING_TO_PLAY)
        playback?.close()
        offlineOpen = false
        navigator.show(Destination.Home)
        navigator.open(Destination.Details(itemId))
        play(itemId).join()
    }

    /** An engine that will not let go is left behind rather than waited for; nothing may hang on it. */
    private suspend fun awaitRelease() {
        releasing?.let { release ->
            if (withTimeoutOrNull(RELEASE_PATIENCE_MS) { release.join() } == null) platform.report("The previous playback engine did not shut down in time")
        }
    }

    /**
     * Ends what is playing, and what has only just stopped: a player closed a moment ago may still be letting go of
     * its video surface, and an application that leaves then takes the surface from under the engine (libmpv 0.37
     * aborts the process on X11).
     */
    suspend fun finishPlayback() {
        playback?.close()?.join()
        awaitRelease()
    }

    internal fun playbackClosed(session: PlaybackSession, release: Job) {
        // What the support report says about the last playback (docs/RELEASE.md, "Diagnostics export").
        runCatching { session.report() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { lastPlayback = it }
        if (playback === session) playback = null
        releasing = release
    }

    // Support ------------------------------------------------------------------------------------

    /** How the most recent playback went, kept until the next one ends; never stored. */
    private var lastPlayback: List<PlaybackReport.Line> = emptyList()

    /** Whether a support report can be handed to the person on this platform. */
    val canSaveSupportReport: Boolean get() = platform.supportReports != null

    /**
     * A support report of this moment, for a bug report (docs/RELEASE.md, "Diagnostics export"): the build, the
     * platform, what the device plays, each source's kind and state, the last playback, and the recent problems.
     * Titles, source names, addresses, and user names are given to it as words to remove; it removes the rest by shape.
     */
    suspend fun supportReport(now: String): SupportReport {
        val (sources, titles) = read {
            // The open profile's view: another profile's accounts are not this viewer's to report (docs/WATCH_STATE.md, "Profiles").
            val records = visibleSources()
            records.map { record ->
                val scope = LibraryScope.OfSource(record.id)
                SupportReport.SourceSummary(
                    kind = sourceKind(record.type), availability = record.availability.name, included = record.included,
                    signInRequired = record.signInRequired, works = movies(scope).size + shows(scope).size,
                )
            } to privateWords()
        }
        val playing = playback?.report().orEmpty().ifEmpty { lastPlayback }
        return SupportReport(
            build = platform.build, generatedAt = now,
            // How many profiles there are, never who: names are the viewers' own (docs/WATCH_STATE.md, "Profiles").
            environment = platform.platformFacts + SupportReport.Fact("Profiles", read { profiles() }.size.toString()),
            capabilities = SupportReport.capabilities(platform.device), sources = sources,
            playback = playing.map { SupportReport.Fact(it.heading, it.text) },
            log = runCatching { platform.recentProblems() }.getOrDefault(emptyList()).takeLast(SUPPORT_LOG_LINES),
            privateValues = titles,
        )
    }

    /** A source's kind in a report: what it is, never what it is called. */
    private fun sourceKind(type: String): String = when (type) {
        "local", "android-folder" -> "Folder"
        "downloads" -> "Downloads"
        "jellyfin" -> "Jellyfin"
        "plex" -> "Plex"
        "smb" -> "SMB share"
        "webdav" -> "WebDAV share"
        else -> "Other"
    }

    /** Makes a support report and hands it to the person; says where it went, or that it could not be made. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun saveSupportReport(): Job = attempt("The support report couldn't be saved. Try again.") {
        val outlet = platform.supportReports ?: return@attempt
        val now = kotlin.time.Clock.System.now().toString().take(19) + "Z"
        val text = supportReport(now).render()
        notify(outlet.deliver("reflux-support-${now.filter(Char::isDigit).take(8)}-${now.filter(Char::isDigit).drop(8).take(6)}.txt", text))
    }

    /** Opens the support page, or says its address where nothing can open it. */
    fun openSupport() {
        if (!platform.openLink(SUPPORT_URL)) notify("Support and feedback: ${SUPPORT_URL.removePrefix("https://")}")
    }

    private fun Library.playableFor(itemId: MediaId, scope: LibraryScope): MediaId? = when (val item = item(itemId)) {
        is Movie, is Episode -> item.id
        is Show -> showDetail(item.id, scope)?.let { detail ->
            detail.nextUp?.id ?: (detail.seasons.firstOrNull { it.season.number > 0 } ?: detail.seasons.firstOrNull())
                ?.episodes?.firstOrNull()?.item?.id
        }
        is Season -> showDetail(item.showId, scope)?.seasons?.firstOrNull { it.season.id == item.id }?.let { season ->
            (season.episodes.firstOrNull { it.watchState?.completed != true } ?: season.episodes.firstOrNull())?.item?.id
        }
        null -> null
    }

    /** What each source really declares, which decides what can reach this device through it. */
    internal val sourceCapabilities: (SourceId) -> Set<dev.reflux.core.source.SourceCapability> = { sources(it)?.descriptor?.capabilities.orEmpty() }

    /** The viewer's languages, which decide which audio a version would play. */
    internal val viewer: TrackPreferences get() = TrackPreferences(platform.preferredLanguages)

    /** Shows [message] briefly; longer messages stay long enough to be read. */
    fun notify(message: String) {
        notice = message
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(NOTICE_MS + NOTICE_MS_PER_CHARACTER * (message.length - NOTICE_SHORT_LENGTH).coerceAtLeast(0))
            notice = null
        }
    }

    // Input --------------------------------------------------------------------------------------

    private val modality = InputModalityTracker(InputModality.POINTER, platform.television)

    /** How the user is interacting right now; the layout follows it (the controller morph). */
    var input: InputState by mutableStateOf(modality.state)
        private set

    /**
     * Whether to say what the buttons in the user's hand do. A controller's are named for where they sit
     * and need it; a remote's are named for what they do and do not.
     */
    val namesButtons: Boolean get() = input.modality == InputModality.DIRECTIONAL && input.controllerFamily != ControllerFamily.TV_REMOTE

    /** Set while a text field has focus, so typing is never taken for shortcuts. */
    var textInputActive by mutableStateOf(false)

    /** Toolkit focus, as far as semantic actions need it. Installed by the root composable. */
    internal var moveFocus: ((SemanticAction) -> Boolean)? = null
    internal var clearFocus: (() -> Unit)? = null
    internal var activateFocused: (() -> Boolean)? = null

    /** The player is one context, everything with focus and lists another: browsing, and the player's menus. */
    private val context: InputContext
        get() = if (watching?.menu == null && watching != null) InputContext.PLAYBACK else InputContext.BROWSE

    /** Reports raw input evidence (pointer travel, touches, controller use) from the shell or the UI. */
    fun onSignal(signal: InputSignal) {
        val next = modality.onSignal(signal)
        if (next != input) input = next
    }

    /**
     * Handles a key from a keyboard, media keys, or a remote. Returns false when the key is not Reflux's to
     * handle, so the toolkit can process it (typing, activating the focused control).
     */
    fun onKey(key: Key, repeat: Boolean = false): Boolean {
        onSignal(InputSignal.KeyPress(key))
        // With what is playing in the mini-player, the viewer is browsing: keys are the page's.
        val browsing = watching == null
        // The player is operated by its keys. Tab would only walk the page behind the video, which nobody can see.
        if (!browsing && key == Key.TAB) return true
        // A field is one line: up and down move between fields, everything else is typing, but for the
        // destination shortcuts, which no typing makes.
        if (browsing && textInputActive && key != Key.ESCAPE && key != Key.ARROW_DOWN && key != Key.ARROW_UP && key !in SHORTCUTS) return false
        val action = InputMapping.map(key, context) ?: return false
        // The toolkit activates the focused control for Enter and Space itself.
        if (browsing && action == SemanticAction.SELECT) return false
        // A held seek key is one seek, made when it is let go: repeats go to the session as repeats.
        if (repeat && !browsing && (action == SemanticAction.SEEK_FORWARD || action == SemanticAction.SEEK_BACKWARD) && profileGate == null) {
            watching?.let { session -> if (session.menu == null && session.error == null) return run { session.seekKey(action, repeat = true); true } }
        }
        return dispatch(action)
    }

    /** A key was let go: the held seek's one seek is made. */
    fun onKeyReleased(key: Key) {
        val action = InputMapping.map(key, context)
        if (action == SemanticAction.SEEK_FORWARD || action == SemanticAction.SEEK_BACKWARD) watching?.seekKeyReleased()
    }

    /** Handles a controller button, named by its position so every controller family works alike. */
    fun onGamepadButton(button: GamepadButton, family: ControllerFamily): Boolean {
        onSignal(InputSignal.GamepadButtonPress(button, family))
        return dispatch(InputMapping.map(button, family, context) ?: return false)
    }

    /** Performs a semantic action, whichever input device expressed it. */
    fun dispatch(action: SemanticAction): Boolean {
        // Asking who is watching covers everything: Back is its own, and lists and fields take the rest.
        profileGate?.let { gate ->
            return when (action) {
                SemanticAction.BACK -> profileGateBack(gate)
                SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT -> moveFocus?.invoke(action) ?: false
                SemanticAction.SELECT -> activateFocused?.invoke() ?: false
                else -> true
            }
        }
        // In the system's window there is no Reflux to act on: the window is the picture, and its controls are the system's.
        if (playback?.presentation == PlayerPresentation.SYSTEM) return false
        watching?.let { return it.handle(action) }
        return when (action) {
            SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                moveFocus?.invoke(action) ?: false
            SemanticAction.SELECT -> activateFocused?.invoke() ?: false
            SemanticAction.BACK -> if (backRoute == BackRoute.SYSTEM_PRESENTATION) platform.enterSystemPresentation() else when {
                // With a keyboard, Escape first lets go of the field. A remote's Back has already put the
                // on-screen keyboard away by the time it gets here, and goes back.
                textInputActive && !platform.television -> {
                    clearFocus?.invoke()
                    true
                }
                folderPicker != null -> {
                    folderPicker?.back()
                    true
                }
                offlineOpen -> {
                    offlineOpen = false
                    true
                }
                // On a television a destination's own page gives Back to the navigation first: Back never
                // leaves a section, or Reflux, from the middle of a row.
                platform.television && navigator.current is Destination.Section && focusNavigation?.invoke() == true -> true
                else -> navigator.back()
            }
            SemanticAction.MENU -> {
                if (folderPicker == null && !offlineOpen) toggleSources()
                true
            }
            SemanticAction.GO_HOME, SemanticAction.GO_MOVIES, SemanticAction.GO_SHOWS, SemanticAction.GO_SEARCH,
            SemanticAction.GO_DOWNLOADS, SemanticAction.GO_SOURCES -> {
                if (folderPicker != null || offlineOpen) return false
                val section = when (action) {
                    SemanticAction.GO_HOME -> Destination.Home
                    SemanticAction.GO_MOVIES -> Destination.Movies
                    SemanticAction.GO_SHOWS -> Destination.Shows
                    SemanticAction.GO_SEARCH -> Destination.Search
                    SemanticAction.GO_DOWNLOADS -> Destination.Downloads
                    else -> Destination.Sources
                }
                if (section !in destinations) return false
                go(section)
                true
            }
            SemanticAction.NEXT_SECTION, SemanticAction.PREVIOUS_SECTION ->
                folderPicker == null && !offlineOpen && step(if (action == SemanticAction.NEXT_SECTION) 1 else -1)
            SemanticAction.FULLSCREEN -> {
                platform.requestFullscreen(!platform.fullscreen)
                true
            }
            // What browsing has no use for still reaches what is playing beside it: media keys, mute, and the key
            // that brings the player back.
            else -> docked?.takeIf { action in DOCKED_ACTIONS }?.handle(action) ?: false
        }
    }

    /**
     * Who handles Back now, from the state alone (docs/planning/M10-PLAYER-SPEC.md, section 4). The platform shell enables
     * exactly one handler for it, so that where Reflux has nothing to do with Back the system's own runs, back-to-home
     * animation and all. It agrees with `dispatch(BACK)` by construction: [BackRoute.SYSTEM] is exactly where that
     * returns false.
     */
    val backRoute: BackRoute
        get() {
            profileGate?.let { return if (it.backLeaves()) BackRoute.SYSTEM else BackRoute.APP }
            val session = playback
            if (session?.presentation == PlayerPresentation.SYSTEM) return BackRoute.SYSTEM
            watching?.let { full ->
                return when {
                    full.menu != null || full.error != null -> BackRoute.APP
                    miniPlayerAvailable -> BackRoute.COLLAPSE
                    else -> BackRoute.APP
                }
            }
            return when {
                textInputActive && !platform.television -> BackRoute.APP
                folderPicker != null || offlineOpen -> BackRoute.APP
                platform.television && navigator.current is Destination.Section -> BackRoute.APP
                navigator.canGoBack -> BackRoute.APP
                // Playing in the dock, and the system has a window for it: leaving is not stopping.
                systemPresentationAvailable && docked?.runningInDock() == true -> BackRoute.SYSTEM_PRESENTATION
                else -> BackRoute.SYSTEM
            }
        }

    private fun ProfileGate.backLeaves(): Boolean = when (this) {
        is ProfileGate.Choose -> required
        is ProfileGate.Unlock -> !forgetting && !profilesShown && required
    }

    // Profiles (docs/WATCH_STATE.md, "Profiles") ----------------------------------------------------------

    /** The profiles of this installation, as last read; one of them is active. */
    var profiles: List<Profile> by mutableStateOf(emptyList())
        private set

    /** The profile everything shown is about now. */
    val activeProfile: Profile? get() = profiles.firstOrNull { it.active }

    /** Whether profiles show at all: an installation with one profile never mentions them. */
    val profilesShown: Boolean get() = profiles.size > 1

    /** Whether the active profile's PIN was entered since it was last opened: a profile with a PIN opens locked. */
    private var unlocked = false

    /**
     * Who is watching, while Reflux asks: at start where it has to (a profile with a PIN, or a television with several
     * profiles), and whenever a profile with a PIN is opened. It covers everything; nothing behind it can be reached.
     */
    var profileGate: ProfileGate? by mutableStateOf(null)
        private set

    /** Whether profiles can have a PIN here: the platform holds a key to check them with. */
    val pinsAvailable: Boolean get() = platform.pinKey != null

    internal suspend fun loadProfiles() {
        profiles = read { profiles() }
    }

    /** Whether Reflux was opened by a launcher card, which names the profile it is for. */
    private var continuedFromOutside = false

    private suspend fun askWhoIsWatchingAtStart() {
        loadProfiles()
        if (continuedFromOutside) return
        val active = activeProfile ?: return
        profileGate = when {
            active.hasPin -> ProfileGate.Unlock(active, required = true)
            // A shared screen with several viewers asks, every time; a phone or a computer remembers who it was.
            platform.television && profilesShown -> ProfileGate.Choose(required = true)
            else -> null
        }
    }

    /** Opens the question of who is watching, from the profile entry. */
    fun askWhoIsWatching() {
        scope.launch {
            loadProfiles()
            profileGate = ProfileGate.Choose(required = false)
        }
    }

    /** The viewer chose [profile]: one with a PIN asks for it first; the one already open just closes the question. */
    fun chooseProfile(profile: Profile): Job = attempt("That profile couldn't be opened. Try again.") {
        if (profile.hasPin && !(profile.active && unlocked)) {
            profileGate = ProfileGate.Unlock(profile, required = (profileGate as? ProfileGate.Choose)?.required == true)
            return@attempt
        }
        if (profile.active) {
            profileGate = null
            return@attempt
        }
        becomeProfile(profile)
    }

    /** Checks the PIN entered for the profile being opened, and opens it when it is right. */
    fun enterPin(pin: String): Job = attempt("That PIN couldn't be checked. Try again.") {
        val gate = profileGate as? ProfileGate.Unlock ?: return@attempt
        val key = platform.pinKey ?: return@attempt
        when (val check = read { checkPin(gate.profile.id, pin, key) }) {
            PinCheck.Accepted -> {
                if (gate.profile.active) {
                    unlocked = true
                    profileGate = null
                } else {
                    becomeProfile(gate.profile)
                    unlocked = true
                }
                gate.then?.let { continueFromOutside(it, gate.profile.id) }
            }
            is PinCheck.Rejected -> profileGate = gate.copy(problem = check.lockedUntilEpochMs?.let(::waitFor) ?: "That's not the PIN. Try again.", attempt = gate.attempt + 1)
            is PinCheck.Locked -> profileGate = gate.copy(problem = waitFor(check.untilEpochMs), attempt = gate.attempt + 1)
        }
    }

    private fun waitFor(untilEpochMs: Long): String {
        val seconds = ((untilEpochMs - library.currentTime()) / 1000).coerceAtLeast(1)
        return if (seconds < 90) "Too many tries. Try again in $seconds seconds." else "Too many tries. Try again in ${(seconds + 59) / 60} minutes."
    }

    /** Asks whether to start the profile whose PIN is being asked for over, or with false takes the question back. */
    fun forgotPin(asking: Boolean = true) {
        (profileGate as? ProfileGate.Unlock)?.let { profileGate = it.copy(forgetting = asking, problem = null) }
    }

    /**
     * Starts the profile whose PIN was forgotten over, once the viewer has confirmed it (docs/WATCH_STATE.md,
     * "Profiles"): this device is signed out of the profile's server accounts, its downloads are deleted, and its
     * watch history, favorites, collections, pinned versions, preferences, and PIN are erased, so nothing the PIN
     * kept private is shown to whoever reset it. The profile stays, open and empty. No server's own history, no
     * media, no shared source or server address, and no other profile is touched.
     */
    fun resetForgottenProfile(): Job = attempt("The profile couldn't be reset. Try again.") {
        val gate = profileGate as? ProfileGate.Unlock ?: return@attempt
        if (!gate.forgetting) return@attempt
        val profile = gate.profile
        finishPlayback()
        val addresses = read { endpoints() }
        read { accountSourcesOf(profile.id) }.forEach { platform.removeSource(it.id) }
        platform.offline?.let { offline -> read { profileDownloadIds(profile.id) }.forEach { withContext(background) { offline.remove(it) } } }
        read {
            resetProfile(profile.id)
            keepEndpoints(addresses)
        }
        loadProfiles()
        val fresh = profiles.firstOrNull { it.id == profile.id } ?: return@attempt
        if (fresh.active) {
            unlocked = true
            profileGate = null
            searchQuery = ""
            lastPlayback = emptyList()
            navigator.show(Destination.Home)
            invalidate()
            watchSources()
        } else {
            becomeProfile(fresh)
        }
        notify("${profile.name} was reset. It no longer has a PIN.")
    }

    private fun profileGateBack(gate: ProfileGate): Boolean {
        if (gate is ProfileGate.Unlock && gate.forgetting) {
            profileGate = gate.copy(forgetting = false)
            return true
        }
        when (gate) {
            is ProfileGate.Unlock -> profileGate = if (profilesShown) ProfileGate.Choose(gate.required) else if (gate.required) return false else null
            // At start, someone has to be watching: Back leaves Reflux instead of getting past the question.
            is ProfileGate.Choose -> if (gate.required) return false else profileGate = null
        }
        return true
    }

    /**
     * Makes [profile] the one watching. What was playing stops first and is recorded as the profile it was playing
     * for; then everything shown is the new profile's, from Home.
     */
    private suspend fun becomeProfile(profile: Profile) {
        finishPlayback()
        offlineOpen = false
        folderPicker = null
        read { switchProfile(profile.id) }
        // Nothing of the last viewer's session is left for the next: what they searched for, how their last playback
        // went, what Reflux was telling them.
        searchQuery = ""
        lastPlayback = emptyList()
        notice = null
        unlocked = false
        loadProfiles()
        profileGate = null
        navigator.show(Destination.Home)
        invalidate()
        watchSources()
        if (profilesShown) notify("Watching as ${profile.name}")
    }

    /** Adds a profile and opens it: a new viewer starts with nothing of anyone else's. */
    fun addProfile(name: String): Job = attempt("The profile couldn't be added. Try again.") {
        val id = read { createProfile(name) }
        loadProfiles()
        profiles.firstOrNull { it.id == id }?.let { becomeProfile(it) }
    }

    fun renameProfile(name: String): Job = attempt("The name couldn't be saved. Try again.") {
        val active = activeProfile ?: return@attempt
        read { renameProfile(active.id, name) }
        loadProfiles()
    }

    /** Gives the active profile a PIN; says so, or why not. */
    fun setPin(pin: String): Job = attempt("The PIN couldn't be saved. Try again.") {
        val active = activeProfile ?: return@attempt
        val key = platform.pinKey ?: return@attempt
        if (!PinPolicy.valid(pin)) return@attempt notify("A PIN is ${PinPolicy.LENGTH} digits.")
        read { setPin(active.id, pin, key) }
        unlocked = true
        loadProfiles()
        invalidate()
        notify("${active.name} now asks for its PIN.")
    }

    fun removePin(): Job = attempt("The PIN couldn't be removed. Try again.") {
        val active = activeProfile ?: return@attempt
        read { removePin(active.id) }
        loadProfiles()
        invalidate()
        notify("${active.name} no longer asks for a PIN.")
    }

    /**
     * Clears the active profile's local watch history: Reflux's own record of what it watched and where it stopped,
     * and with it Continue Watching. No server, media, download, or other profile is touched.
     */
    fun clearWatchHistory(): Job = attempt("Watch history couldn't be cleared. Try again.") {
        val active = activeProfile ?: return@attempt
        read { clearWatchHistory(active.id) }
        invalidate()
        notify("Watch history cleared on this device. Your servers keep their own.")
    }

    /**
     * Deletes another profile and everything that was its own: its server accounts are signed out of and removed, its
     * downloads deleted, its history gone. The active profile is not deleted from inside itself.
     */
    fun deleteProfile(profile: Profile): Job = attempt("The profile couldn't be deleted. Try again.") {
        if (profile.active || profiles.size <= 1) return@attempt
        val accounts = read { accountSourcesOf(profile.id) }
        accounts.forEach {
            stopPlayingFrom(it.id)
            platform.removeSource(it.id)
        }
        platform.offline?.let { offline -> read { profileDownloadIds(profile.id) }.forEach { withContext(background) { offline.remove(it) } } }
        read { deleteProfile(profile.id) }
        loadProfiles()
        watchSources()
        invalidate()
        notify("${profile.name} was deleted from this device.")
    }

    private companion object {
        /** What a session in the mini-player still answers to while the viewer browses. */
        val DOCKED_ACTIONS = setOf(
            SemanticAction.PLAY_PAUSE, SemanticAction.STOP, SemanticAction.NEXT, SemanticAction.PREVIOUS, SemanticAction.SEEK_FORWARD,
            SemanticAction.SEEK_BACKWARD, SemanticAction.MUTE, SemanticAction.VOLUME_UP, SemanticAction.VOLUME_DOWN, SemanticAction.MINI_PLAYER,
        )
        val SHORTCUTS = setOf(Key.SHORTCUT_1, Key.SHORTCUT_2, Key.SHORTCUT_3, Key.SHORTCUT_4, Key.SHORTCUT_5, Key.SHORTCUT_6)
        private const val SUPPORT_LOG_LINES = 200
        const val NOTICE_MS = 4_500L
        const val DOWNLOAD_PROGRESS_MS = 500L
        const val RELEASE_PATIENCE_MS = 5_000L
        const val NOTICE_SHORT_LENGTH = 60
        const val NOTICE_MS_PER_CHARACTER = 45L
    }
}

/**
 * Reads from the library off the UI thread and keeps the result current: it reloads whenever the library
 * or user state changes, keeping the previous value on screen meanwhile so nothing flickers.
 */
@Composable
fun <T> AppState.libraryState(vararg keys: Any?, block: Library.() -> T): State<T?> {
    val state = remember(*keys) { mutableStateOf<T?>(null) }
    LaunchedEffect(revision, *keys) { state.value = read { block() } }
    return state
}

/** As [libraryState], for what shows downloads: it also follows their progress ([AppState.downloadRevision]). */
@Composable
fun <T> AppState.offlineState(vararg keys: Any?, block: Library.() -> T): State<T?> {
    val state = remember(*keys) { mutableStateOf<T?>(null) }
    LaunchedEffect(revision, downloadRevision, *keys) { state.value = read { block() } }
    return state
}

/** Where the Media Atmosphere choice is kept ([Library.setting]). */
internal const val MEDIA_ATMOSPHERE = "appearance.mediaAtmosphere"

/** Where people get help and send feedback: the website's support page. */
const val SUPPORT_URL = "https://reflux.rsxdevelops.dev/support"

/** Asking who is watching ([AppState.profileGate]). */
sealed interface ProfileGate {
    /** Whether it has to be answered: at start, or for a launcher card; Back then leaves Reflux rather than getting past it. */
    val required: Boolean

    /** Choosing a profile. */
    data class Choose(override val required: Boolean) : ProfileGate

    /**
     * Entering [profile]'s PIN. [problem] says what was wrong with the last try, and [attempt] counts them so the same
     * words are said again. [then] is a work a launcher card asked to continue once the profile is open.
     */
    data class Unlock(
        val profile: Profile,
        override val required: Boolean,
        val problem: String? = null,
        val attempt: Int = 0,
        val then: MediaId? = null,
        /** The viewer said they forgot the PIN and is being asked whether to start the profile over. */
        val forgetting: Boolean = false,
    ) : ProfileGate
}

