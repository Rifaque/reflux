package dev.reflux.ui.state

import androidx.compose.runtime.mutableStateListOf
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
import dev.reflux.library.ProfileDeletion
import dev.reflux.library.deleteProfile
import dev.reflux.library.profileDownloadIds
import dev.reflux.library.profiles
import dev.reflux.library.removePin
import dev.reflux.library.removePinConfirmedBySystem
import dev.reflux.library.changePin
import dev.reflux.library.PinChange
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
     * nothing to restore (docs/history/milestones/M10-PLAYER-SPEC.md, section 4.4).
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

    /** What Search looks in: everything, or movies or shows alone (B-057). Kept for the session. */
    var searchScope by mutableStateOf(SearchScope.ALL)

    /** A short, transient message ("Its source isn't connected"). */
    var notice: String? by mutableStateOf(null)
        private set

    /** What the background pipeline is doing, for a quiet progress indicator. */
    val activity: StateFlow<PipelineActivity> get() = activityShown ?: pipeline.activity

    /** For layout tests: an activity held on screen for as long as it is looked at, instead of the pipeline's. */
    internal var activityShown: StateFlow<PipelineActivity>? by mutableStateOf(null)

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
        scope.launch {
            // Interface haptics are kept only once turned on, rumble only once turned off: an untouched device has the defaults.
            runCatching { read { setting(Haptics.INTERFACE_HAPTICS) to setting(Haptics.CONTROLLER_RUMBLE) } }.getOrNull()?.let { (device, rumble) ->
                haptics.interfaceOn = device == "on"
                haptics.rumbleOn = rumble != "off"
            }
        }
        scope.launch { runCatching { read { this.onlineMetadata } }.getOrNull()?.let { onlineMetadata = it } }
        scope.launch { runCatching { read { setting(CONTROLLER_HINTS) } }.getOrNull()?.let { hintsHidden = it == "hidden" } }
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

    /**
     * The one way anything is felt (F-001, F-002): interface haptics off until the viewer turns them on, rumble on
     * until they turn it off, every request checked here.
     */
    val haptics: Haptics by lazy { Haptics({ platform.deviceHaptics }, { platform.controllerRumble }, { library.currentTime() }) }

    /** Whether the input being handled now came from a controller's buttons, for its rumble. */
    private var fromController = false

    /**
     * Feedback for [event] on whatever the viewer is using: a controller's motors when one is in the hand, the device's
     * vibration for a touch, nothing for a pointer, a keyboard, or a remote.
     */
    fun feel(event: HapticEvent) {
        when {
            fromController || (input.modality == InputModality.DIRECTIONAL && input.controllerFamily != null && input.controllerFamily != ControllerFamily.TV_REMOTE) -> haptics.controller(event)
            input.modality == InputModality.TOUCH -> haptics.touch(event)
        }
    }

    /**
     * A move asked of a controller, felt on it (F-002): a light tick where focus went, a firmer one at the end of a row or a
     * page. Never for a keyboard, a remote, or a pointer, and nothing while a picture is playing (that is not here: playback
     * keeps its own keys).
     */
    private fun feelMove(moved: Boolean) {
        if (!fromController || moveFocus == null) return
        haptics.controller(if (moved) HapticEvent.NAVIGATE else HapticEvent.BOUNDARY)
    }

    /** A controller's Select that took effect, felt on it as a confirmation (F-002). */
    private fun feelChoice(activated: Boolean) {
        if (activated && fromController) haptics.controller(HapticEvent.CONFIRM)
    }

    fun setInterfaceHaptics(on: Boolean): Job {
        haptics.interfaceOn = on
        if (on) haptics.touch(HapticEvent.TOGGLE)
        return attempt("That couldn't be saved. Try again.") { read { setSetting(Haptics.INTERFACE_HAPTICS, if (on) "on" else null) } }
    }

    fun setControllerRumble(on: Boolean): Job {
        haptics.rumbleOn = on
        if (on) haptics.controller(HapticEvent.TOGGLE) else haptics.stopRumble()
        return attempt("That couldn't be saved. Try again.") { read { setSetting(Haptics.CONTROLLER_RUMBLE, if (on) null else "off") } }
    }

    /**
     * Whether the controller's button hints are hidden (B-055), in browsing and in the player alike: LT hides them all, its
     * own included (the owner's walk, 2026-11-20), and LT brings them back. Kept across restarts. Shown by default.
     */
    var hintsHidden by mutableStateOf(false)
        private set

    fun toggleHints(): Job {
        hintsHidden = !hintsHidden
        val hidden = hintsHidden
        return attempt("That couldn't be saved. Try again.") { read { setSetting(CONTROLLER_HINTS, if (hidden) "hidden" else null) } }
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

    /** Whether a pull at the top of a page is checking the library for changes now (B-048). */
    var pullRefreshing by mutableStateOf(false)
        private set

    /**
     * A pull at the top of Home, Movies, or Shows (B-048): checks every source for what was added, removed, or changed,
     * through the same pipeline as Sources' refresh and the automatic checks, so what is already known (titles, artwork)
     * is not fetched again, and a source that cannot be reached keeps its works as they were. A pull while one is
     * running starts nothing new, and the pipeline joins a refresh already waiting its turn: no storm of scans. The sign
     * goes when the work is done, whatever came of it; a failure is said, and nothing shown is taken away.
     */
    fun pullToRefresh(): Job? {
        if (pullRefreshing) return null
        pullRefreshing = true
        haptics.touch(HapticEvent.CONFIRM)
        return refresh().also { job -> job.invokeOnCompletion { pullRefreshing = false } }
    }

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

    /**
     * Y's way to the navigation and back (F-P2), where the navigation is beside the page; null where it is not (a phone's
     * bottom bar) or nothing is shown. State, so the button prompts offer Y only where it does something.
     */
    internal var toggleNavigation: (() -> Boolean)? by mutableStateOf(null)

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

    /**
     * Stops reaching a server at one of its addresses (B-003); the source, its account, library, and other addresses
     * stay. A source's only address is not removed this way: removing the source is.
     */
    fun removeSourceAddress(id: SourceId, address: String): Job = attempt("That address couldn't be removed.") {
        val connections = platform.connections ?: return@attempt
        if (withContext(background) { connections.removeAddress(id, address) }) {
            invalidate()
            notify("Reflux no longer uses $address.")
        }
    }

    /** Takes [itemId] out of the active profile's favorites, whether or not anything can play it now (B-033). */
    fun removeFavorite(itemId: MediaId): Job = attempt("Your favorites couldn't be updated.") {
        read { setFavorite(itemId, false) }
        invalidate()
        notify("Removed from your favorites.")
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

    /**
     * Set while a text field has focus, so typing is never taken for shortcuts. Every field reports it itself
     * (`Modifier.textEntry`, through [editing]); no screen sets it (B-019 to B-021).
     */
    var textInputActive by mutableStateOf(false)

    /**
     * Whether the system's on-screen keyboard is up (the Android shell says so from its insets). A phone's bottom bar
     * steps aside only while it is: a field that still has focus after the keyboard was put away keeps its page's
     * navigation (B-036).
     */
    var systemKeyboardShown by mutableStateOf(false)

    /** The fields with focus now: typing is active while any has it. */
    internal val editing = dev.reflux.ui.components.TextEditing { textInputActive = it }

    /**
     * The Reflux keyboard, while a controller or a remote types into a field with it (B-023). It is above everything,
     * the question of who is watching included, and takes every direction, confirm, and Back until it closes.
     */
    var keyboard: KeyboardSession? by mutableStateOf(null)
        private set

    /** Which of the keyboard's presentations is shown, decided with hysteresis while it is open (F-P4, §7). */
    private var keyboardArbiter: dev.reflux.core.input.KeyboardModeArbiter? = null

    /** The library's film and show titles, read when a search keyboard opens, for its completions (D2). */
    private var completionTitles: List<String> = emptyList()

    /**
     * Opens the Reflux keyboard for a field: as the controller's grid, or, where a pointer is the input in use on a desk
     * (Windows, Linux), as a keyboard in the system's own layout (F-P4, D3; US with a note where it cannot be read).
     */
    fun openKeyboard(request: KeyboardRequest) {
        val desk = platform.deskKeyboard && !platform.television
        val mode = if (desk && input.modality == InputModality.POINTER) KeyboardMode.POINTER else KeyboardMode.CONTROLLER
        val system = if (desk) runCatching { platform.keyboardLayout() }.getOrNull() else null
        keyboard = KeyboardSession(request, mode, system ?: KeyboardLayouts.US, if (desk && system == null) "US layout" else null)
        keyboardArbiter = dev.reflux.core.input.KeyboardModeArbiter(
            if (mode == KeyboardMode.POINTER) dev.reflux.core.input.KeyboardModeArbiter.Mode.POINTER else dev.reflux.core.input.KeyboardModeArbiter.Mode.CONTROLLER,
        )
        if (request.kind == InputKind.SEARCH) scope.launch {
            completionTitles = runCatching { read { (movies() + shows()).map { it.item.title }.distinct() } }.getOrDefault(emptyList())
        }
    }

    /**
     * The title completions for [query] (owner's decision D2): titles from the library only, no network and no metadata
     * request; case- and accent-insensitive; titles starting with it first, then titles with a word starting with it;
     * at most [limit]. Empty for an empty query.
     */
    fun completionsFor(query: String, limit: Int): List<String> {
        val wanted = dev.reflux.core.identify.TitleText.key(query.trim())
        if (wanted.isEmpty()) return emptyList()
        val scored = completionTitles.mapNotNull { title ->
            val folded = dev.reflux.core.identify.TitleText.key(title)
            when {
                folded == wanted -> null
                folded.startsWith(wanted) -> 0 to title
                folded.split(' ').any { it.startsWith(wanted) } -> 1 to title
                else -> null
            }
        }
        return scored.sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second.length }.thenBy { it.second }).take(limit).map { it.second }
    }

    /** A completion chosen by a pointer or a finger: it fills the field; the keyboard stays open (D2). */
    fun completeOnKeyboard(index: Int) {
        val session = keyboard ?: return
        session.complete(index)
    }

    /** The pointer travelled [dp] over the keyboard: the desk presentation once that is clearly deliberate (§7). */
    fun keyboardPointerTravel(dp: Float) {
        val session = keyboard ?: return
        if (!platform.deskKeyboard || platform.television) return
        if (keyboardArbiter?.pointerMoved(dp, overKeyboard = true, nowMs = UiClock.nowMs()) == true) {
            session.show(KeyboardMode.POINTER)
            // The hints change with the keyboard, not later on a click: a hints bar leaving under a press moved the keys away
            // from the pointer before its release, and that first click typed nothing (the owner's walk, 2026-11-20).
            if (input.modality != InputModality.POINTER) input = modality.onSignal(InputSignal.PointerClick)
        }
    }

    private fun showKeyboardFor(controller: Boolean) {
        val session = keyboard ?: return
        val arbiter = keyboardArbiter ?: return
        val now = UiClock.nowMs()
        if (controller) {
            if (arbiter.controllerPressed(now) || session.mode != KeyboardMode.CONTROLLER) session.show(KeyboardMode.CONTROLLER)
        } else if (platform.deskKeyboard && !platform.television) {
            if (arbiter.clicked(now) || session.mode != KeyboardMode.POINTER) session.show(KeyboardMode.POINTER)
        }
    }

    /** Closes the Reflux keyboard, only if it is [field]'s when one is named. The field keeps focus and its text. */
    fun closeKeyboard(field: Any? = null) {
        if (field == null || keyboard?.request?.field === field) {
            keyboard = null
            keyboardArbiter = null
        }
    }

    /** A key of the Reflux keyboard pressed where it is, by a finger or a pointer. */
    fun pressKey(row: Int, column: Int) {
        val session = keyboard ?: return
        session.choose(row, column)
        pressKey(session.selected)
    }

    /**
     * [key] pressed where it is drawn, by a pointer or a finger: typed as drawn when it was pressed, then the desk
     * presentation is shown where there is one (§7: the click types the key under it, then the morph runs).
     */
    fun pressKey(key: KeyboardKey) {
        val session = keyboard ?: return
        val outcome = session.click(key)
        showKeyboardFor(controller = false)
        when (outcome) {
            KeyboardSession.Outcome.DONE -> {
                keyboard = null
                session.request.done()
            }
            KeyboardSession.Outcome.HIDE -> keyboard = null
            else -> Unit
        }
    }

    /** A semantic action while the Reflux keyboard is open: its own, Back to close it, and only media beside it. */
    private fun typeOnKeyboard(session: KeyboardSession, action: SemanticAction): Boolean {
        if (action == SemanticAction.BACK) {
            keyboard = null
            return true
        }
        if (action in DOCKED_ACTIONS && action != SemanticAction.MINI_PLAYER) return docked?.handle(action) ?: true
        if (action == SemanticAction.FULLSCREEN) return true
        // A controller's or a remote's press shows the controller's grid (§7), with focus mapped by key (§9), and acts at once
        // (the owner's walk, 2026-11-20: a first A that only revealed focus read as a key that did not work).
        showKeyboardFor(controller = true)
        when (session.handle(action)) {
            KeyboardSession.Outcome.DONE -> {
                keyboard = null
                session.request.done()
            }
            KeyboardSession.Outcome.HIDE -> keyboard = null
            KeyboardSession.Outcome.EDGE -> if (fromController) haptics.controller(HapticEvent.BOUNDARY)
            KeyboardSession.Outcome.HANDLED -> Unit
        }
        return true
    }

    /**
     * A physical keyboard's key while the Reflux keyboard is open, off a television (owner's decision D5): a separate
     * way to type. It never presses, lights or moves a virtual key, and never changes the presentation; letters and
     * arrows are the field's. Enter finishes (or chooses the completion it is on), Escape closes (or leaves the
     * completions), and Tab moves along the completions.
     */
    private fun physicalKeyOnKeyboard(session: KeyboardSession, key: Key): Boolean {
        val at = session.completion
        return when (key) {
            Key.TAB -> if (at == null) session.enterCompletions(0) else session.enterCompletions((at + 1) % session.completions.size.coerceAtLeast(1))
            Key.ARROW_LEFT -> at != null && session.enterCompletions((at - 1).coerceAtLeast(0))
            Key.ARROW_RIGHT -> at != null && session.enterCompletions(at + 1)
            Key.ARROW_UP, Key.ARROW_DOWN -> at != null && true.also { session.leaveCompletions() }
            Key.ENTER -> {
                if (at != null) {
                    session.complete(at)
                    session.leaveCompletions()
                } else {
                    keyboard = null
                    session.request.done()
                }
                true
            }
            Key.ESCAPE -> {
                if (at != null) session.leaveCompletions() else keyboard = null
                true
            }
            // Media keys still reach what is playing beside the keyboard.
            else -> InputMapping.map(key, InputContext.TEXT_ENTRY)?.takeIf { it in DOCKED_ACTIONS }?.let(::dispatch) ?: false
        }
    }

    /** Toolkit focus, as far as semantic actions need it. Installed by the root composable. */
    internal var moveFocus: ((SemanticAction) -> Boolean)? = null
    internal var clearFocus: (() -> Unit)? = null
    internal var activateFocused: (() -> Boolean)? = null

    /**
     * The player is one context, everything with focus and lists another: browsing, and the player's menus. The Reflux
     * keyboard is a third, above both while it is open.
     */
    private val context: InputContext
        get() = when {
            keyboard != null -> InputContext.TEXT_ENTRY
            watching?.menu == null && watching != null -> InputContext.PLAYBACK
            else -> InputContext.BROWSE
        }

    /** Reports raw input evidence (pointer travel, touches, controller use) from the shell or the UI. */
    fun onSignal(signal: InputSignal) {
        val next = modality.onSignal(signal)
        if (next != input) {
            val pointerTookOver = next.modality == InputModality.POINTER && input.modality != InputModality.POINTER
            input = next
            // The keyboard follows the hand the hints follow (the owner's walk, 2026-11-20): the pointer taking over anywhere
            // shows the desk keyboard, never hints for a pointer over a controller's grid. Not on a click's press: the keys
            // would move under it before its release; a click types its key, then shows the desk keyboard (pressKey).
            if (pointerTookOver && signal is InputSignal.PointerMove) pointerTookOverKeyboard()
        }
    }

    /** The pointer became the input in use while the keyboard is open: its desk presentation, once the arbiter's hold allows. */
    private fun pointerTookOverKeyboard() {
        val session = keyboard ?: return
        if (!platform.deskKeyboard || platform.television || session.mode == KeyboardMode.POINTER) return
        val arbiter = keyboardArbiter ?: return
        if (arbiter.pointerTookOver(UiClock.nowMs())) {
            session.show(KeyboardMode.POINTER)
        } else {
            // Within the hold right after a controller's press: shown when it ends, if the pointer is still the input then.
            scope.launch {
                kotlinx.coroutines.delay(dev.reflux.core.input.KeyboardModeArbiter.HOLD_MS)
                if (keyboard === session && input.modality == InputModality.POINTER && arbiter.pointerTookOver(UiClock.nowMs())) session.show(KeyboardMode.POINTER)
            }
        }
    }

    /** A click anywhere on the keyboard's panel (not only on a key) shows the desk presentation, as a click on a key does. */
    fun keyboardClicked() {
        if (keyboard == null) return
        showKeyboardFor(controller = false)
    }

    /**
     * Handles a key from a keyboard, media keys, or a remote. Returns false when the key is not Reflux's to
     * handle, so the toolkit can process it (typing, activating the focused control).
     */
    fun onKey(key: Key, repeat: Boolean = false): Boolean {
        onSignal(InputSignal.KeyPress(key))
        // The Reflux keyboard first (B-025's priority: the active text editor): its arrows, OK, and Back. Every other
        // key is the field's, which still has focus beneath it: a physical keyboard types straight into it.
        keyboard?.let { session ->
            // A television's keys are its remote's, which type on the Reflux keyboard; elsewhere they are a physical
            // keyboard's, a separate way to type (D5).
            return if (platform.television) InputMapping.map(key, InputContext.TEXT_ENTRY)?.let(::dispatch) ?: false
            else physicalKeyOnKeyboard(session, key)
        }
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

    /**
     * Whether a controller button held down repeats, beyond the directions (which always do): on the Reflux keyboard, erase
     * and the cursor buttons, as a held Backspace or arrow does (the walk on Windows, 2026-11-19). Read on the controller
     * thread, so only a glance at state.
     */
    fun repeatsWhileHeld(button: GamepadButton, family: ControllerFamily): Boolean =
        keyboard != null && InputMapping.map(button, family, InputContext.TEXT_ENTRY) in HELD_TYPING

    /**
     * Whether Back waits for its release, because holding it means something else (B-052): while the mini-player is up and
     * nothing is over it, a short Back is Back, and a held one closes what plays. Read on the controller thread too.
     */
    val backWaitsForRelease: Boolean
        get() = docked != null && watching == null && keyboard == null && profileGate == null && folderPicker == null && !offlineOpen

    /**
     * Closes what plays in the mini-player, for good (B-052): playback stops and the mini-player goes; focus stays where it
     * was on the page. False when there is no mini-player.
     */
    /** Whether the mini-player is up with nothing over it, so the right stick's press can bring it back to the full player. */
    val miniPlayerExpands: Boolean
        get() = docked != null && watching == null && keyboard == null && profileGate == null && folderPicker == null && !offlineOpen

    /** The mini-player back to the full player (the right stick's press). False when there is no mini-player. */
    fun expandMiniPlayer(): Boolean {
        val session = docked ?: return false
        session.expand()
        if (fromController) haptics.controller(HapticEvent.CONFIRM)
        return true
    }

    fun closeMiniPlayer(): Boolean {
        val session = docked ?: return false
        session.close()
        if (fromController) haptics.controller(HapticEvent.CONFIRM)
        return true
    }

    /** A controller's button held down past [BACK_HOLD_MS] where holding means something (B-052). True when it did something. */
    fun onGamepadHold(button: GamepadButton, family: ControllerFamily): Boolean {
        backHolding = false
        if (button != InputMapping.cancelButton(family) || !backWaitsForRelease) return false
        fromController = true
        return try {
            closeMiniPlayer()
        } finally {
            fromController = false
        }
    }

    /** A remote's Back (or a keyboard's Escape) held down past [BACK_HOLD_MS]: the same as a controller's (B-052). */
    fun onBackHeld(): Boolean {
        backHolding = false
        return backWaitsForRelease && closeMiniPlayer()
    }

    /** Whether Back is being held now, where holding it closes the mini-player: its prompt's ring fills meanwhile (B-052). */
    var backHolding by mutableStateOf(false)
        private set

    /** Back went down where holding it means something ([backWaitsForRelease]), or was let go, or acted. */
    fun backHold(down: Boolean) {
        backHolding = down && backWaitsForRelease
    }

    /** Handles a controller button, named by its position so every controller family works alike. */
    fun onGamepadButton(button: GamepadButton, family: ControllerFamily): Boolean {
        onSignal(InputSignal.GamepadButtonPress(button, family))
        // RT closes the mini-player while it is up and nothing is over it (B-052, the owner's choice: View stays fullscreen). The
        // triggers have no other browsing meaning, so nothing else is taken from them; in the player RT is still faster.
        if (button == GamepadButton.TRIGGER_RIGHT && docked != null && watching == null && keyboard == null && profileGate == null) {
            fromController = true
            return try {
                closeMiniPlayer()
            } finally {
                fromController = false
            }
        }
        // Pressing the right stick, the player's own stick (its scenes, B-029), brings the mini-player back to the full player
        // from wherever focus is (the owner's walk, 2026-11-20). Nothing else uses it, so it never plays, closes, or opens
        // anything else.
        if (button == GamepadButton.STICK_RIGHT && miniPlayerExpands) {
            fromController = true
            return try {
                expandMiniPlayer()
            } finally {
                fromController = false
            }
        }
        val action = InputMapping.map(button, family, context) ?: return false
        fromController = true
        return try {
            dispatch(action)
        } finally {
            fromController = false
        }
    }

    /**
     * The right stick of a controller (B-029): its horizontal axis while it moves or is held, or null when the
     * controller holding it is gone. In the full player it moves through the scenes; it has no browsing meaning.
     */
    fun onRightStick(value: Float?, family: ControllerFamily) {
        val session = playback
        if (value == null) return run { session?.stickLost() }
        onSignal(InputSignal.GamepadAxis(kotlin.math.abs(value), family))
        val playing = watching
        if (playing == null || keyboard != null || profileGate != null) return run { session?.stickLost() }
        playing.rightStick(value)
    }

    // Media Quick View (F-P3) ---------------------------------------------------------------------

    /** The film or show whose Quick View is open, over the page; null while none is. */
    var quickView: MediaId? by mutableStateOf(null)
        private set

    /** The focused control's contextual action, wired by the shell's composition (F-P3); false when it has none. */
    internal var focusedContext: (() -> Boolean)? = null

    /**
     * Opens Quick View for [itemId] (a film or a show) over the page: from a card's X, right-click, long touch, or a
     * remote's Menu. Not while something else covers the page, and not from inside the player.
     */
    fun openQuickView(itemId: MediaId) {
        if (watching != null || keyboard != null || profileGate != null || folderPicker != null || offlineOpen) return
        quickView = itemId
    }

    /** Closes Quick View; focus goes back to the card that opened it. */
    fun closeQuickView() {
        quickView = null
    }

    /** Preview sheets already fetched, kept across playbacks (B-030). */
    val trickplaySheets = TrickplaySheets()

    /** Performs a semantic action, whichever input device expressed it. */
    fun dispatch(action: SemanticAction): Boolean {
        // The Reflux keyboard is above everything while it is open, the question of who is watching included.
        keyboard?.let { return typeOnKeyboard(it, action) }
        // Asking who is watching covers everything: Back is its own, and lists and fields take the rest.
        profileGate?.let { gate ->
            return when (action) {
                SemanticAction.BACK -> profileGateBack(gate)
                SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                    (moveFocus?.invoke(action) ?: false).also { moved -> feelMove(moved) }
                SemanticAction.SELECT -> (activateFocused?.invoke() ?: false).also(::feelChoice)
                else -> true
            }
        }
        // Quick View is in front of the page (F-P3): Back, X, or Menu closes it; the arrows and Select are its own.
        quickView?.let {
            return when (action) {
                SemanticAction.BACK, SemanticAction.CONTEXT, SemanticAction.MENU -> true.also { closeQuickView() }
                SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                    (moveFocus?.invoke(action) ?: false).also { moved -> feelMove(moved) }
                SemanticAction.SELECT -> (activateFocused?.invoke() ?: false).also(::feelChoice)
                else -> true
            }
        }
        // In the system's window there is no Reflux to act on: the window is the picture, and its controls are the system's.
        if (playback?.presentation == PlayerPresentation.SYSTEM) return false
        // LT's hints, the player's included, are the app's: one preference everywhere.
        if (action == SemanticAction.TOGGLE_HINTS) {
            toggleHints()
            return true
        }
        watching?.let { return it.handle(action) }
        return when (action) {
            SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                (moveFocus?.invoke(action) ?: false).also { moved -> feelMove(moved) }
            SemanticAction.SELECT -> (activateFocused?.invoke() ?: false).also(::feelChoice)
            SemanticAction.BACK -> if (backRoute == BackRoute.SYSTEM_PRESENTATION) platform.enterSystemPresentation() else when {
                // With a keyboard, Escape first lets go of the field. A remote's Back has already put the
                // on-screen keyboard away by the time it gets here, and goes back. So has a touchscreen's: the system
                // keyboard takes the first Back itself, so the next one is the viewer's way back, not a field to let
                // go of that they cannot see has focus (B-036).
                textInputActive && !platform.television && input.modality != InputModality.TOUCH -> {
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
                panelCloses.isNotEmpty() -> {
                    panelCloses.last().invoke()
                    true
                }
                // On a television a destination's own page gives Back to the navigation first: Back never
                // leaves a section, or Reflux, from the middle of a row.
                platform.television && navigator.current is Destination.Section && focusNavigation?.invoke() == true -> true
                else -> navigator.back()
            }
            // A remote's Menu on a focused film or show is its Quick View (F-P3); anywhere else it opens Sources, as
            // a controller's Start always does (Start is not a context key: CLAUDE.md, "Controller mapping").
            SemanticAction.MENU -> {
                if (folderPicker == null && !offlineOpen && (fromController || focusedContext?.invoke() != true)) toggleSources()
                true
            }
            SemanticAction.CONTEXT -> folderPicker == null && !offlineOpen && focusedContext?.invoke() == true
            SemanticAction.TOGGLE_HINTS -> {
                toggleHints()
                true
            }
            SemanticAction.TOGGLE_NAVIGATION ->
                (folderPicker == null && !offlineOpen && toggleNavigation?.invoke() == true).also { moved -> if (moved && fromController) haptics.controller(HapticEvent.NAVIGATE) }
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
     * Who handles Back now, from the state alone (docs/history/milestones/M10-PLAYER-SPEC.md, section 4). The platform shell enables
     * exactly one handler for it, so that where Reflux has nothing to do with Back the system's own runs, back-to-home
     * animation and all. It agrees with `dispatch(BACK)` by construction: [BackRoute.SYSTEM] is exactly where that
     * returns false.
     */
    val backRoute: BackRoute
        get() {
            if (keyboard != null) return BackRoute.APP
            profileGate?.let { return if (it.backLeaves()) BackRoute.SYSTEM else BackRoute.APP }
            // Quick View is in front: the system's Back (a gesture, a phone's button) closes it, as B does (F-P3).
            if (quickView != null) return BackRoute.APP
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
                textInputActive && !platform.television && input.modality != InputModality.TOUCH -> BackRoute.APP
                folderPicker != null || offlineOpen -> BackRoute.APP
                panelCloses.isNotEmpty() -> BackRoute.APP
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

    /**
     * Panels open in place on the page (a confirmation, a rename, a PIN panel), innermost last. Back closes the innermost
     * before anything else on the page: before, a remote's Back or a controller's B went to the navigation or left the page
     * with the panel still open (Phase 2's close, the Google TV walk, 2026-11-20).
     */
    private val panelCloses = mutableStateListOf<() -> Unit>()

    internal fun openPanel(close: () -> Unit) {
        panelCloses += close
    }

    internal fun closedPanel(close: () -> Unit) {
        panelCloses.remove(close)
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
        // Every device opens the profile it had (B-016; DECISIONS.md, 2026-11-20): a television no longer asks at every
        // start. A profile with a PIN still opens locked, and "Who's watching?" is a press away (B-017).
        profileGate = if (active.hasPin) ProfileGate.Unlock(active, required = true) else null
    }

    /** Opens the question of who is watching, from the profile entry. */
    fun askWhoIsWatching() {
        scope.launch {
            loadProfiles()
            profileGate = ProfileGate.Choose(required = false)
        }
    }

    /**
     * Logs the viewer out of the open profile (B-017): nothing of theirs is deleted or signed out of, but what they
     * were doing stops, nothing of their session is left on screen, a PIN is asked for again, and Reflux asks who is
     * watching, as at a start where someone has to choose. Switching ([askWhoIsWatching]) asks without leaving.
     */
    fun logOut(): Job = attempt("Couldn't log out. Try again.") {
        if (!profilesShown) return@attempt
        finishPlayback()
        offlineOpen = false
        folderPicker = null
        closeKeyboard()
        searchQuery = ""
        lastPlayback = emptyList()
        notice = null
        unlocked = false
        navigator.show(Destination.Home)
        loadProfiles()
        profileGate = ProfileGate.Choose(required = true)
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
            is PinCheck.Rejected -> {
                profileGate = gate.copy(problem = check.lockedUntilEpochMs?.let(::waitFor) ?: "That's not the PIN. Try again.", attempt = gate.attempt + 1)
                feel(HapticEvent.ERROR)
            }
            is PinCheck.Locked -> {
                profileGate = gate.copy(problem = waitFor(check.untilEpochMs), attempt = gate.attempt + 1)
                feel(HapticEvent.ERROR)
            }
        }
    }

    private fun waitFor(untilEpochMs: Long): String {
        val seconds = ((untilEpochMs - library.currentTime()) / 1000).coerceAtLeast(1)
        return if (seconds < 90) "Too many tries. Try again in $seconds seconds." else "Too many tries. Try again in ${(seconds + 59) / 60} minutes."
    }

    /**
     * Forgot PIN (DECISIONS.md, 2026-11-19): what to do about a profile whose PIN is forgotten. With [asking], the
     * choices: keep the profile as it is, reset it (erasing what the PIN kept private), or, where the device has a
     * lock of its own, have the system confirm its owner and remove the PIN while keeping everything. False takes the
     * question back: Keep profile, which changes nothing.
     */
    fun forgotPin(asking: Boolean = true) {
        (profileGate as? ProfileGate.Unlock)?.let { profileGate = it.copy(forgetting = asking, resetting = false, problem = null) }
    }

    /** Asks to confirm resetting the profile whose PIN was forgotten, the destructive choice; false steps back from it. */
    fun confirmProfileReset(asking: Boolean = true) {
        (profileGate as? ProfileGate.Unlock)?.takeIf { it.forgetting }?.let { profileGate = it.copy(resetting = asking, problem = null) }
    }

    /** Whether Forgot PIN can offer the system's confirmation here: the platform has it, and a lock is set up. */
    val systemConfirmsOwner: Boolean get() = runCatching { platform.systemAuthentication?.available() == true }.getOrDefault(false)

    /**
     * Removes a forgotten PIN once the system has confirmed the device's owner (its biometric or screen-lock prompt),
     * keeping everything the profile has, and opens it. Nothing Reflux asks for is involved, and the old PIN is never
     * revealed. Cancelled, failed, or unavailable, nothing changes and nothing opens; the viewer can still keep the
     * profile or reset it.
     */
    fun removeForgottenPinWithSystem(): Job = attempt("The PIN couldn't be removed. Try again.") {
        val gate = profileGate as? ProfileGate.Unlock ?: return@attempt
        if (!gate.forgetting) return@attempt
        val system = platform.systemAuthentication?.takeIf { it.available() }
        if (system == null) {
            profileGate = gate.copy(problem = "This device has no screen lock to confirm it's you.", attempt = gate.attempt + 1)
            return@attempt
        }
        val answer = system.confirm("Remove ${gate.profile.name}'s PIN", "Confirm it's you to remove the PIN. ${gate.profile.name}'s history and settings stay.")
        // The question may have been answered or left meanwhile: only the same one, still asking, goes on.
        val now = profileGate as? ProfileGate.Unlock
        if (now == null || now.profile.id != gate.profile.id || !now.forgetting) return@attempt
        if (answer != SystemConfirmation.CONFIRMED) {
            profileGate = now.copy(
                problem = when (answer) {
                    SystemConfirmation.CANCELLED -> "Nothing was changed."
                    SystemConfirmation.UNAVAILABLE -> "Your device couldn't confirm it's you here. Nothing was changed."
                    else -> "Your device didn't confirm it's you. Nothing was changed."
                },
                attempt = now.attempt + 1,
            )
            return@attempt
        }
        read { removePinConfirmedBySystem(gate.profile.id) }
        loadProfiles()
        val fresh = profiles.firstOrNull { it.id == gate.profile.id } ?: return@attempt
        if (fresh.active) {
            unlocked = true
            profileGate = null
        } else {
            becomeProfile(fresh)
            unlocked = true
        }
        gate.then?.let { continueFromOutside(it, fresh.id) }
        invalidate()
        notify("${fresh.name}'s PIN was removed. You can set a new one in Settings.")
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
        // Only from its own confirmation: Reset profile, then its warning, then confirming that.
        if (!gate.forgetting || !gate.resetting) return@attempt
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
        if (gate is ProfileGate.Unlock && gate.resetting) {
            profileGate = gate.copy(resetting = false, problem = null)
            return true
        }
        if (gate is ProfileGate.Unlock && gate.forgetting) {
            profileGate = gate.copy(forgetting = false, problem = null)
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
    /**
     * Gives the open profile a PIN, or, when it has one, replaces it given its [current] PIN, checked by the library in
     * the same transaction as the change (B-049). When the current PIN is not accepted, [pinRefused] says why.
     */
    fun setPin(pin: String, current: String? = null): Job = attempt("The PIN couldn't be saved. Try again.") {
        val active = activeProfile ?: return@attempt
        val key = platform.pinKey ?: return@attempt
        if (!PinPolicy.valid(pin)) return@attempt notify("A PIN is ${PinPolicy.LENGTH} digits.")
        val hasPin = read { profiles() }.firstOrNull { it.id == active.id }?.hasPin == true
        if (hasPin) {
            when (val outcome = read { changePin(active.id, current, pin, key) }) {
                PinChange.Changed -> pinRefused = null
                PinChange.Invalid -> return@attempt notify("A PIN is ${PinPolicy.LENGTH} digits.")
                is PinChange.Refused -> return@attempt refusePinChange(outcome.check)
            }
        } else if (!read { setPin(active.id, pin, key) }) {
            return@attempt
        }
        unlocked = true
        loadProfiles()
        invalidate()
        notify(if (hasPin) "${active.name}'s PIN was changed." else "${active.name} now asks for its PIN.")
    }

    /**
     * Removes the open profile's PIN, only with that [current] PIN (B-049): checked in the same transaction that removes
     * it, with the counting and waiting of opening it. Nothing else of the profile, and nothing of another, changes.
     */
    fun removePin(current: String): Job = attempt("The PIN couldn't be removed. Try again.") {
        val active = activeProfile ?: return@attempt
        when (val outcome = read { removePin(active.id, current, platform.pinKey) }) {
            PinChange.Changed -> {
                pinRefused = null
                loadProfiles()
                invalidate()
                notify("${active.name} no longer asks for a PIN.")
            }
            PinChange.Invalid -> Unit
            is PinChange.Refused -> refusePinChange(outcome.check)
        }
    }

    /** Why the current PIN given to change or remove the open profile's PIN was not accepted; null when it was not refused. */
    var pinRefused: PinRefusal? by mutableStateOf(null)

    /** Forgets [pinRefused], when the question that asked is closed. */
    fun dismissPinRefusal() {
        pinRefused = null
    }

    private suspend fun refusePinChange(check: PinCheck) {
        feel(HapticEvent.ERROR)
        pinRefused = PinRefusal(refusal(check), (pinRefused?.attempt ?: 0) + 1)
        loadProfiles()
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
    fun deleteProfile(profile: Profile, pin: String? = null): Job = attempt("The profile couldn't be deleted. Try again.") {
        if (profile.active || profiles.size <= 1) return@attempt
        // A profile with a PIN is deleted only with its own PIN (docs/WATCH_STATE.md, "Profiles"). It is checked before
        // anything is signed out of or deleted, and again by the library in the same transaction that deletes.
        val key = platform.pinKey
        // Whether it has a PIN is read now, not from the page that asked: nothing may go on what the page last knew.
        val current = read { profiles() }.firstOrNull { it.id == profile.id } ?: return@attempt
        if (current.hasPin) {
            val check = if (pin == null || key == null) PinCheck.Rejected(null) else read { checkPin(profile.id, pin, key) }
            if (check != PinCheck.Accepted) {
                deletionRefused = DeletionRefusal(profile.id, refusal(check), deletionRefused?.attempt?.plus(1) ?: 1)
                loadProfiles()
                return@attempt
            }
        }
        deletionRefused = null
        val accounts = read { accountSourcesOf(profile.id) }
        accounts.forEach {
            stopPlayingFrom(it.id)
            platform.removeSource(it.id)
        }
        platform.offline?.let { offline -> read { profileDownloadIds(profile.id) }.forEach { withContext(background) { offline.remove(it) } } }
        val outcome = read { deleteProfile(profile.id, pin, key) }
        loadProfiles()
        watchSources()
        invalidate()
        when (outcome) {
            ProfileDeletion.Deleted -> notify("${profile.name} was deleted from this device.")
            ProfileDeletion.Kept -> Unit
            // Only if the PIN changed between the two checks; its accounts and downloads are already gone, as asked.
            is ProfileDeletion.Refused -> deletionRefused = DeletionRefusal(profile.id, refusal(outcome.check), 1)
        }
    }

    /** Why the PIN given to delete a profile was not accepted, for the question that asked for it; null when none was refused. */
    var deletionRefused: DeletionRefusal? by mutableStateOf(null)

    private fun refusal(check: PinCheck): String = when (check) {
        is PinCheck.Locked -> waitFor(check.untilEpochMs)
        is PinCheck.Rejected -> check.lockedUntilEpochMs?.let(::waitFor) ?: "That's not the PIN. Try again."
        PinCheck.Accepted -> ""
    }

    private companion object {
        /** What a session in the mini-player still answers to while the viewer browses. */
        /** The device setting that keeps the controller's hints hidden (B-055); absent: shown. */
        const val CONTROLLER_HINTS = "controller_hints"

        /** What a held controller button keeps doing on the Reflux keyboard: erasing, and moving the cursor. */
        val HELD_TYPING = setOf(SemanticAction.TEXT_ERASE, SemanticAction.CURSOR_BACK, SemanticAction.CURSOR_FORWARD)

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
        /** The viewer said they forgot the PIN and is being asked what to do: keep the profile, or reset it. */
        val forgetting: Boolean = false,
        /** The viewer chose to reset the profile and is being asked to confirm it, having been told what goes. */
        val resetting: Boolean = false,
    ) : ProfileGate
}


/** The current PIN given to change or remove a PIN was not accepted: [problem] says why; [attempt] counts tries. */
data class PinRefusal(val problem: String, val attempt: Int)

/** The PIN given to delete [profile] was not accepted: [problem] says why, and [attempt] counts tries so it is said again. */
data class DeletionRefusal(val profile: ProfileId, val problem: String, val attempt: Int)

/** How long Back is held to close the mini-player (B-052): longer than any tap, short enough not to feel stuck. */
const val BACK_HOLD_MS = 700L
