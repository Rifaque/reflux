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
import dev.reflux.library.PipelineActivity
import dev.reflux.library.planPlayback
import dev.reflux.library.setWatchedEverywhere
import kotlinx.coroutines.CancellationException
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

    /** What is playing, if anything. Rendered above everything else by the shell. */
    var playback: PlaybackSession? by mutableStateOf(null)
        private set

    var sourcesOpen by mutableStateOf(false)

    /** The folder picker Reflux draws itself, while it is open (see [Platform.folderBrowser]). */
    var folderPicker: FolderPicker? by mutableStateOf(null)
        private set

    var searchQuery by mutableStateOf("")

    /** A short, transient message ("Its source is offline"). */
    var notice: String? by mutableStateOf(null)
        private set

    /** What the background pipeline is doing, for a quiet progress indicator. */
    val activity: StateFlow<PipelineActivity> get() = pipeline.activity

    /** Whether titles and artwork could not be fetched last time; the library works without them. */
    val metadataUnreachable: StateFlow<Boolean> get() = pipeline.metadataUnreachable

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
            sourcesOpen = false
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
     */
    fun play(itemId: MediaId, versionId: VersionId? = null, resume: Boolean = true): Job = attempt("This couldn't be played right now.") {
        if (playback != null || starting) return@attempt
        starting = true
        try {
            // One engine and one video surface at a time: the player that was just closed is gone first.
            // An engine that will not let go is left behind rather than waited for; playing must stay possible.
            releasing?.let { release ->
                if (withTimeoutOrNull(RELEASE_PATIENCE_MS) { release.join() } == null) platform.report("The previous playback engine did not shut down in time")
            }
            val playable = read { playableFor(itemId) } ?: return@attempt notify(Problems.NOTHING_TO_PLAY)
            val plan = read { planPlayback(playable, platform.device, sources, versionId, resume) }
                ?: return@attempt notify(read { unplayableReason(playable, platform.device, sources) })
            if (!platform.canPlay) return@attempt notify(Problems.noEngine(platform.playbackSetupHint))
            val player = platform.createPlayer() ?: run {
                platform.report("The playback engine is installed but could not be started")
                return@attempt notify(Problems.PLAYBACK_DID_NOT_START)
            }
            val session = PlaybackSession(this@AppState, player)
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

    internal fun playbackClosed(session: PlaybackSession, release: Job) {
        if (playback === session) playback = null
        releasing = release
    }

    private fun Library.playableFor(itemId: MediaId): MediaId? = when (val item = item(itemId)) {
        is Movie, is Episode -> item.id
        is Show -> showDetail(item.id)?.let { detail ->
            detail.nextUp?.id ?: (detail.seasons.firstOrNull { it.season.number > 0 } ?: detail.seasons.firstOrNull())
                ?.episodes?.firstOrNull()?.item?.id
        }
        is Season -> showDetail(item.showId)?.seasons?.firstOrNull { it.season.id == item.id }?.let { season ->
            (season.episodes.firstOrNull { it.watchState?.completed != true } ?: season.episodes.firstOrNull())?.item?.id
        }
        null -> null
    }

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
        if (browsing && textInputActive && key != Key.ESCAPE && key != Key.ARROW_DOWN) return false
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
                sourcesOpen -> {
                    sourcesOpen = false
                    true
                }
                else -> navigator.back()
            }
            SemanticAction.MENU -> {
                if (folderPicker == null) sourcesOpen = !sourcesOpen
                true
            }
            SemanticAction.FULLSCREEN -> {
                platform.requestFullscreen(!platform.fullscreen)
                true
            }
            else -> false
        }
    }

    private companion object {
        const val NOTICE_MS = 4_500L
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
