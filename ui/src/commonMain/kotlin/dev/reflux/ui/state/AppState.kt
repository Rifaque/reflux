package dev.reflux.ui.state

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
import kotlinx.coroutines.CancellationException
import dev.reflux.core.metadata.ProviderAttribution
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    internal suspend fun <T> read(block: suspend Library.() -> T): T = withContext(background) { library.block() }

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

    /** The global destinations this platform has, in the navigation's order. */
    val destinations: List<Destination.Section> get() = Destinations.filter { it != Destination.Downloads || keepsCopies }

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

    fun removeSource(id: SourceId): Job = attempt("That source couldn't be removed.") {
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
        if (playback != null || starting) return@attempt
        starting = true
        try {
            // One engine and one video surface at a time: the player that was just closed is gone first.
            awaitRelease()
            val playable = read { playableFor(itemId, scope) } ?: return@attempt notify(Problems.NOTHING_TO_PLAY)
            val plan = read { planPlayback(playable, platform.device, sources, versionId, resume, preferences = viewer, scope = scope) }
                ?: return@attempt notify(read { unplayableReason(playable, platform.device, sources, viewer, scope) })
            if (!platform.canPlay) return@attempt notify(Problems.noEngine(platform.playbackSetupHint))
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
    fun continueFromOutside(itemId: MediaId): Job = scope.launch {
        if (read { item(itemId) } == null) return@launch notify(Problems.NOTHING_TO_PLAY)
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
        if (playback === session) playback = null
        releasing = release
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
        get() = if (playback?.menu == null && playback != null) InputContext.PLAYBACK else InputContext.BROWSE

    /** Reports raw input evidence (pointer travel, touches, controller use) from the shell or the UI. */
    fun onSignal(signal: InputSignal) {
        val next = modality.onSignal(signal)
        if (next != input) input = next
    }

    /**
     * Handles a key from a keyboard, media keys, or a remote. Returns false when the key is not Reflux's to
     * handle, so the toolkit can process it (typing, activating the focused control).
     */
    fun onKey(key: Key): Boolean {
        onSignal(InputSignal.KeyPress(key))
        val browsing = playback == null
        // The player is operated by its keys. Tab would only walk the page behind the video, which nobody can see.
        if (!browsing && key == Key.TAB) return true
        // A field is one line: up and down move between fields, everything else is typing, but for the
        // destination shortcuts, which no typing makes.
        if (browsing && textInputActive && key != Key.ESCAPE && key != Key.ARROW_DOWN && key != Key.ARROW_UP && key !in SHORTCUTS) return false
        val action = InputMapping.map(key, context) ?: return false
        // The toolkit activates the focused control for Enter and Space itself.
        if (browsing && action == SemanticAction.SELECT) return false
        return dispatch(action)
    }

    /** Handles a controller button, named by its position so every controller family works alike. */
    fun onGamepadButton(button: GamepadButton, family: ControllerFamily): Boolean {
        onSignal(InputSignal.GamepadButtonPress(button, family))
        return dispatch(InputMapping.map(button, family, context) ?: return false)
    }

    /** Performs a semantic action, whichever input device expressed it. */
    fun dispatch(action: SemanticAction): Boolean {
        playback?.let { return it.handle(action) }
        return when (action) {
            SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                moveFocus?.invoke(action) ?: false
            SemanticAction.SELECT -> activateFocused?.invoke() ?: false
            SemanticAction.BACK -> when {
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
            else -> false
        }
    }

    private companion object {
        val SHORTCUTS = setOf(Key.SHORTCUT_1, Key.SHORTCUT_2, Key.SHORTCUT_3, Key.SHORTCUT_4, Key.SHORTCUT_5, Key.SHORTCUT_6)
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
