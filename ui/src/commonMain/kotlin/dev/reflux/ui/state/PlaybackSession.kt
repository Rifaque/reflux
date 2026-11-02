package dev.reflux.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.AutoplayPolicy
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.PlanPart
import dev.reflux.core.playback.PlanReason
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackReport
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlaybackEvidence
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.TrackType
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SkipSegment
import dev.reflux.core.playback.SkipSegments
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.source.NegotiatingSource
import dev.reflux.core.source.PlaybackEvidenceSource
import dev.reflux.core.source.SourceCapability
import dev.reflux.library.Library
import dev.reflux.library.LibraryEntry
import dev.reflux.library.PlaybackPlan
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.planPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** The post-play prompt: a countdown into [entry], or a "still watching?" question when [asks] is set. */
data class UpNext(val entry: LibraryEntry, val totalMs: Long, val remainingMs: Long, val asks: Boolean)

/**
 * The player's lists, one level below the transport controls. [EPISODES] is the way to the episode before
 * and after for hands that hold nothing with a next button on it.
 */
enum class PlayerMenu(val title: String) { EPISODES("Episodes"), CHAPTERS("Chapters"), AUDIO("Audio"), SUBTITLES("Subtitles"), INFO("Playback info"), SPEED("Speed") }

/** One line of a [PlayerMenu]; [selected] marks what is in effect now. */
data class MenuOption(val label: String, val detail: String? = null, val selected: Boolean = false)

/**
 * One visit to the player: the work being played, its neighbours, and what happens when it ends.
 *
 * The player UI renders [state] and forwards semantic actions to [handle]; engine-independent playback
 * behavior (default tracks, watch reporting, skipping) stays in the core `PlaybackController`.
 */
@Stable
class PlaybackSession internal constructor(
    private val app: AppState,
    private val player: Player,
) {
    private val scope: CoroutineScope = app.scope + SupervisorJob(app.scope.coroutineContext[Job])
    private val autoplay = AutoplayPolicy()
    private val wasFullscreen = app.platform.fullscreen
    private var controller: PlaybackController? = null
    private var reporter: SerialWatchReporter? = null
    private var sourceId: SourceId? = null
    private var itemId: MediaId? = null
    private var countdown: Job? = null
    private var waiting: Job? = null
    private var waitedTooLong = false
    private var begun = false
    private var endHandled = false
    private var silence: Job? = null
    private var evidenceAsked = false
    private var evidenceJob: Job? = null
    private var current: PlaybackPlan? = null

    /** A failure has already been planned around once for this work: once is all it gets. */
    private var replanned = false

    /** The engine's refusal of this load's starting place has been dealt with. */
    private var placeChecked = false

    /** The audio track last asked for by position, which is the viewer's if they chose one in the player. */
    private var chosenAudio: Int? = null
    private var closing: Job? = null
    private val closed: Boolean get() = closing != null

    val state: StateFlow<PlayerState> get() = player.state

    var entry: LibraryEntry? by mutableStateOf(null)
        private set

    /** The headline shown in the player: the movie or show title. */
    var title by mutableStateOf("")
        private set

    /** The line under it: `S1 · E2 · Cat's in the Bag...` or the year. */
    var subtitle: String? by mutableStateOf(null)
        private set

    var previous: LibraryEntry? by mutableStateOf(null)
        private set

    var next: LibraryEntry? by mutableStateOf(null)
        private set

    var upNext: UpNext? by mutableStateOf(null)
        private set

    /** Set when playback failed; the player explains it and offers the way back. */
    var error: PlaybackError? by mutableStateOf(null)
        private set

    /** The open menu, if any. While one is open, navigation and select act on it instead of on playback. */
    var menu: PlayerMenu? by mutableStateOf(null)
        private set

    /** The option of [menu] that Select would choose; moved by navigation, so menus work without a pointer. */
    var highlighted by mutableIntStateOf(0)
        private set

    /** Playing with no sound because no audio track could be used. Said once, and the picture goes on. */
    var silent by mutableStateOf(false)
        private set

    /** What the source says happened to this playback, once it has been asked. For diagnostics and tests. */
    var evidence: PlaybackEvidence? by mutableStateOf(null)
        private set

    /** What was planned beside what happened, once the source has been asked. For diagnostics and tests. */
    var comparison: PlaybackComparison? by mutableStateOf(null)
        private set

    /** Advances on every user interaction, so the player UI can reveal its controls. */
    var interactions by mutableIntStateOf(0)
        private set

    init {
        player.setVolume(app.volume)
        player.setMuted(app.muted)
        scope.launch { player.state.collect(::onState) }
    }

    internal suspend fun load(plan: PlaybackPlan) {
        val itemId = plan.itemId
        val details = app.read {
            val entry = entry(itemId)
            Details(entry, entry?.item?.let { heading(it) } ?: (plan.request.title to null), entry?.item?.let { neighbours(it) } ?: (null to null))
        }
        controller?.stop()
        countdown?.cancel()
        upNext = null
        error = null
        menu = null
        endHandled = false
        begun = false
        waitedTooLong = false
        waiting?.cancel()
        waiting = null
        silence?.cancel()
        silence = null
        silent = false
        evidence = null
        comparison = null
        evidenceAsked = false
        // What it would find now is this load's stream, and it would hold that against the last load's plan.
        evidenceJob?.cancel()
        evidenceJob = null
        placeChecked = false
        if (this.itemId != itemId) {
            replanned = false
            chosenAudio = null
        }
        current = plan
        this.itemId = itemId
        entry = details.entry
        title = details.heading.first
        subtitle = details.heading.second
        previous = details.neighbours.first
        next = details.neighbours.second
        sourceId = plan.version.version.location.sourceId
        val reporter = SerialWatchReporter(
            SyncingWatchReporter(app.library, plan.version.version, plan.source, app.scope + app.background),
            app.scope,
            app.background,
        )
        this.reporter = reporter
        controller = PlaybackController(
            player = player,
            itemId = itemId,
            reporter = reporter,
            preferences = app.viewer,
            onSkip = { forward -> switchTo(if (forward) next else previous) },
            onSeekRefused = ::seekRefused,
        ).also { it.start(scope, plan.request) }
        // The engine may already say what it said for the last load; the wait for this one starts now.
        watchWaiting(player.state.value.status)
        Problems.handoff(plan.resume)?.let(app::notify)
    }

    /**
     * The viewer's patience, measured on the player's states and nothing else: [PATIENCE_MS] of continuous
     * waiting (loading, or playback that wants to run and has no data) becomes a stated failure, once. An
     * engine that cannot start, or a source that stops sending without closing its connection, reports
     * nothing at all, so the silence itself is the failure. Playing from what is buffered is not waiting,
     * time paused is not waiting, and a wait that ends restarts the count. If playback goes on after all,
     * the message makes way for it.
     */
    private fun watchWaiting(status: PlayerStatus) {
        if (status !in WAITING) {
            waiting?.cancel()
            waiting = null
        } else if (waiting == null && error == null) {
            waiting = scope.launch {
                delay(PATIENCE_MS)
                giveUpWaiting()
            }
        }
    }

    private var subtitlesLost = 0

    private fun giveUpWaiting() {
        if (error != null || player.state.value.status !in WAITING) return
        waitedTooLong = true
        error = PlaybackError(if (begun) Problems.SOURCE_STOPPED else Problems.PLAYBACK_STALLED, null, canRetry = true)
        menu = null
        app.platform.report(
            if (begun) "Playback of \"$title\" waited ${PATIENCE_MS / 1000} s for data" else "Playback of \"$title\" did not start within ${PATIENCE_MS / 1000} s",
        )
        // The playback failed; whether the source is there is for a check of the source to say. Only a check:
        // reading the source again would end the playback's session on a media server while it waits.
        sourceId?.let(app::checkAvailability)
    }

    /**
     * A playback with sound and no track in use is not normal playback, though the engine calls it playing. It
     * has to last [SILENCE_MS]: an engine settles on its tracks a moment after it starts. There is no conversion to
     * fall back on, so the picture goes on and the viewer is told once (docs/PLAYBACK.md).
     */
    private fun watchSilence(state: PlayerState) {
        if (!state.audioUnavailable) {
            silence?.cancel()
            silence = null
        } else if (silence == null && !silent) {
            silence = scope.launch {
                delay(SILENCE_MS)
                val now = player.state.value
                if (!now.audioUnavailable) return@launch
                // An attempt has shown this audio does not play here: the source may still convert it.
                if (planAround(PlanPart.AUDIO)) return@launch
                silent = true
                app.notify(Problems.NO_SOUND)
                val codecs = (now.audioTracks.map { it.codec ?: "unknown" } + now.unsupportedAudioCodecs).distinct().joinToString(" ")
                app.platform.report("Playback of \"$title\" has audio ($codecs) and none of it is playing")
            }
        }
    }

    /** A subtitle a server was making and never delivered is a thing the viewer chose and is not getting. */
    private fun watchSubtitles(state: PlayerState) {
        val lost = state.subtitlesNotLoaded > subtitlesLost
        subtitlesLost = state.subtitlesNotLoaded
        if (!lost) return
        app.notify(Problems.SUBTITLE_UNAVAILABLE)
        app.platform.report("Playback of \"$title\": a subtitle the source was to deliver did not arrive")
    }

    /**
     * Compares what happened with what was planned, once, off the player's way, after the first report has reached
     * the source. A source that keeps no account still has the engine's side to compare.
     */
    private fun askForEvidence() {
        val plan = current ?: return
        if (evidenceAsked) return
        evidenceAsked = true
        evidenceJob = scope.launch {
            delay(EVIDENCE_AFTER_MS)
            val path = plan.version.version.location.path
            val found = (plan.source as? PlaybackEvidenceSource)?.let { source -> withContext(app.background) { source.playbackEvidence(path) } }
            evidence = found
            found?.let { app.platform.report("Playback evidence: ${it.summary()}") }
            val planned = plan.planned ?: return@launch
            val compared = PlaybackComparison.of(planned, found, player.state.value)
            comparison = compared
            when {
                compared.mismatches.isNotEmpty() -> app.platform.report("Playback of \"$title\" is not what was planned: ${compared.summary()}. Playback info: ${info(planned, compared)}")
                found != null -> app.platform.report("Planned against actual: ${compared.summary()}. Playback info: ${info(planned, compared)}")
            }
        }
    }

    /**
     * The one way a run-time failure changes a plan: an attempt at the original has shown that [part] cannot play it,
     * which is an unknown resolved, and the source can convert it. It happens once for a work, keeps the track that
     * was playing, and never moves to another language. Anything else stays the failure it is (docs/PLAYBACK.md).
     *
     * @param from where the new plan starts, if not where the viewer is now.
     * @return whether a new plan is on its way.
     */
    private fun planAround(part: PlanPart, from: Long? = whereTheViewerIs()): Boolean {
        val plan = current ?: return false
        val planned = plan.planned ?: return false
        val requirements = plan.requirements ?: return false
        val item = itemId ?: return false
        val needed = when (part) {
            PlanPart.VIDEO -> SourceCapability.SERVER_TRANSCODE
            PlanPart.CONTAINER -> SourceCapability.SERVER_REMUX
            else -> SourceCapability.SERVER_AUDIO_CONVERSION
        }
        if (replanned || needed !in requirements.source || planned.delivery != DeliveryMode.DIRECT_PLAY) return false
        // The server has already said no: asking again would only restart the picture.
        if (PlanReason.NEGOTIATION_FAILED in planned.reasons) return false
        val track = chosenAudio ?: planned.audioIndex
        if (part == PlanPart.AUDIO && track == null) return false
        replanned = true
        app.platform.report("Playback of \"$title\": the original $part did not play, so it is being planned again")
        controller?.stop(final = false)
        replan(
            item, "Planning playback again failed", "This couldn't be played right now.", continuing = true, version = plan.version.version.id, from = from,
            audio = when {
                part == PlanPart.AUDIO -> AudioChoice.Explicit(track!!)
                // A remux carries one track: the one the viewer moved to in the player, if they did.
                part == PlanPart.CONTAINER && chosenAudio != null -> AudioChoice.Explicit(chosenAudio!!)
                else -> requirements.audio
            },
            subtitle = requirements.subtitle,
            originalFailed = requirements.originalFailed + part,
        )
        return true
    }

    /**
     * The viewer asked for a place in media the engine has said it cannot move in. A seek sent to it would go back to
     * the beginning, so none is: the source remuxes from there if it can, and otherwise the viewer is told and stays put.
     */
    private fun seekRefused(positionMs: Long) {
        if (planAround(PlanPart.CONTAINER, from = positionMs)) return
        app.notify(Problems.CANNOT_SEEK)
        app.platform.report("Playback of \"$title\": the engine cannot seek in this media")
    }

    /**
     * Playback was to start at a place and the engine, which cannot move in this media, began at the beginning. It is
     * dealt with at once, inside the first minute, where [dev.reflux.core.watch.WatchRules] still keeps the saved place.
     */
    private fun watchPlace(state: PlayerState) {
        if (placeChecked || state.status != PlayerStatus.PLAYING || state.seekable == null) return
        placeChecked = true
        val wanted = current?.request?.startPositionMs?.takeIf { it > 0 } ?: return
        if (state.seekable == true || planAround(PlanPart.CONTAINER, from = wanted)) return
        app.notify(Problems.PLACE_NOT_REACHED)
        app.platform.report("Playback of \"$title\": the engine cannot start at the saved place in this media")
    }

    /** Stops whatever a source is doing for the stream now open. */
    private suspend fun releaseStream() {
        val plan = current ?: return
        val source = plan.source as? NegotiatingSource ?: return
        withContext(app.background) { source.release(plan.version.version.location.path) }
    }

    /**
     * Try again after a failure that may pass: plans the playback afresh from where it was, as pressing Play
     * would. The source is checked, Best Version chooses again (another copy may now be the one), and the
     * handoff applies. The position reached is kept as progress first; trying again never ends a work.
     */
    fun retry() {
        val current = itemId
        if (current == null || closed || error?.canRetry != true) return
        touch()
        error = null
        waitedTooLong = false
        controller?.stop(final = false)
        replan(current, "Trying playback again failed", "This couldn't be played right now.", continuing = true)
    }

    /** Handles a semantic action while the player is open. Every action counts as "still watching". */
    fun handle(action: SemanticAction): Boolean {
        touch()
        val open = menu
        val prompt = upNext
        when {
            action == SemanticAction.STOP -> close()
            action == SemanticAction.FULLSCREEN -> app.platform.requestFullscreen(!app.platform.fullscreen)
            open != null -> handleInMenu(open, action)
            // Select takes the failure's first offer, Try again where there is one; back is always the way back.
            error != null -> when {
                action == SemanticAction.BACK -> close()
                action in LEAVING -> if (error?.canRetry == true) retry() else close()
            }
            action == SemanticAction.MENU -> showMenu(defaultMenu())
            action == SemanticAction.BACK -> close()
            action == SemanticAction.PLAY_PAUSE || action == SemanticAction.SELECT -> when {
                prompt == null -> controller?.handle(SemanticAction.PLAY_PAUSE)
                prompt.asks -> continueWatching()
                else -> playNext()
            }
            // Where loudness is the device's own (a television's remote, a phone's keys), down is the way
            // to everything the player offers beyond play and seek, and up takes what is on offer above
            // the controls (skipping an intro), or just brings the controls back.
            action == SemanticAction.NAVIGATE_DOWN && app.platform.systemVolume -> showMenu(defaultMenu())
            action == SemanticAction.NAVIGATE_UP && app.platform.systemVolume -> skippable()?.let(::skip)
            // Otherwise, without directional focus in the player, up and down are the volume, as in every desktop player.
            action == SemanticAction.NAVIGATE_UP -> controller?.handle(SemanticAction.VOLUME_UP)
            action == SemanticAction.NAVIGATE_DOWN -> controller?.handle(SemanticAction.VOLUME_DOWN)
            else -> controller?.handle(action)
        }
        return true
    }

    private fun handleInMenu(open: PlayerMenu, action: SemanticAction) {
        when (action) {
            SemanticAction.NAVIGATE_UP -> highlighted = (highlighted - 1).coerceAtLeast(0)
            SemanticAction.NAVIGATE_DOWN -> highlighted = (highlighted + 1).coerceAtMost(options(open).lastIndex)
            SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT -> {
                val all = menus()
                all.getOrNull(all.indexOf(open) + if (action == SemanticAction.NAVIGATE_RIGHT) 1 else -1)?.let(::showMenu)
            }
            SemanticAction.SELECT, SemanticAction.PLAY_PAUSE -> choose(open, highlighted)
            SemanticAction.BACK, SemanticAction.MENU -> menu = null
            else -> controller?.handle(action)
        }
    }

    /** The menu a button opens: subtitles, the one most often wanted, when there are any. */
    private fun defaultMenu(): PlayerMenu = menus().let { all -> PlayerMenu.SUBTITLES.takeIf { it in all } ?: all.first { it != PlayerMenu.EPISODES } }

    /** The intro, recap, or credits the position is in, which the player offers to skip. */
    fun skippable(state: PlayerState = player.state.value): SkipSegment? =
        SkipSegments.at(SkipSegments.from(state.chapters, state.durationMs), state.positionMs)

    /** Any user activity in the player that is not a semantic action (pointer movement, scrubbing). */
    fun touch() {
        autoplay.onInteraction()
        interactions++
    }

    // Menus --------------------------------------------------------------------------------------

    /** The menus that have something to choose from for what is playing, in the order they are offered. */
    fun menus(state: PlayerState = player.state.value): List<PlayerMenu> = listOfNotNull(
        PlayerMenu.EPISODES.takeIf { next != null || previous != null },
        PlayerMenu.CHAPTERS.takeIf { state.chapters.size > 1 },
        PlayerMenu.AUDIO.takeIf { (fileAudio() ?: state.audioTracks).size > 1 },
        PlayerMenu.SUBTITLES.takeIf { (fileSubtitles() ?: state.subtitleTracks).isNotEmpty() },
        PlayerMenu.SPEED,
        PlayerMenu.INFO.takeIf { current?.planned != null },
    )

    private fun info(planned: dev.reflux.core.playback.PlannedPlayback, compared: PlaybackComparison) = PlaybackReport.summary(PlaybackReport.of(planned, compared, player.state.value))

    /** Why this playback is what it is, in words, for the Playback info list (PlaybackReport). */
    fun report(state: PlayerState = player.state.value): List<PlaybackReport.Line> =
        current?.planned?.let { PlaybackReport.of(it, comparison, state) }.orEmpty()

    /** A converted stream carries one audio track and no subtitles: what can be chosen is what the file has. */
    private val converted: Boolean get() = current?.planned?.let { it.delivery != DeliveryMode.DIRECT_PLAY } == true

    private fun fileAudio(): List<PlayerTrack>? = if (!converted) null else current?.version?.version?.stream?.audio?.mapIndexed { index, track ->
        PlayerTrack(index.toString(), TrackType.AUDIO, track.language, codec = track.codec.name.lowercase(), channels = track.channels, default = track.default, position = index)
    }

    private fun fileSubtitles(): List<PlayerTrack>? {
        if (!converted) return null
        val version = current?.version?.version ?: return null
        return version.stream.subtitles.mapIndexed { index, track ->
            PlayerTrack("e$index", TrackType.SUBTITLE, track.language, codec = track.format.name.lowercase(), forced = track.forced, hearingImpaired = track.hearingImpaired, position = index)
        } + version.externalSubtitles.mapIndexed { index, track ->
            PlayerTrack("x$index", TrackType.SUBTITLE, track.language, codec = track.format.name.lowercase(), forced = track.forced, hearingImpaired = track.hearingImpaired, external = true, position = index)
        }
    }

    /** Another track of a converted stream is another stream, from where the viewer is. */
    private fun chooseInFile(audio: AudioChoice?, subtitle: SubtitleChoice?) {
        val plan = current ?: return
        val requirements = plan.requirements ?: return
        val item = itemId ?: return
        touch()
        val place = whereTheViewerIs()
        controller?.stop(final = false)
        replan(
            item, "Changing a track failed", "That track couldn't be played right now.", continuing = true, version = plan.version.version.id, from = place,
            audio = audio ?: requirements.audio, subtitle = subtitle ?: requirements.subtitle, originalFailed = requirements.originalFailed,
        )
    }

    fun options(menu: PlayerMenu, state: PlayerState = player.state.value): List<MenuOption> = when (menu) {
        PlayerMenu.EPISODES -> neighbours().map { (entry, forward) -> MenuOption(episodeLabel(entry), if (forward) "Next" else "Previous") }
        PlayerMenu.CHAPTERS -> {
            val current = state.chapters.indexOfLast { it.startMs <= state.positionMs }
            state.chapters.mapIndexed { index, chapter ->
                MenuOption(chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}", Formatting.clock(chapter.startMs), index == current)
            }
        }
        PlayerMenu.AUDIO -> fileAudio()?.map { MenuOption(Formatting.track(it), selected = it.position == current?.planned?.audioIndex) }
            ?: state.audioTracks.map { MenuOption(Formatting.track(it), selected = it.id == state.selectedAudioId) }
        PlayerMenu.SUBTITLES -> fileSubtitles()?.let { tracks ->
            val planned = current?.planned
            listOf(MenuOption("Off", selected = planned?.subtitleIndex == null)) +
                tracks.map { MenuOption(Formatting.track(it), selected = it.position == planned?.subtitleIndex && it.external == planned?.subtitleExternal) }
        } ?: (
            listOf(MenuOption("Off", selected = state.selectedSubtitleId == null)) +
                state.subtitleTracks.map { MenuOption(Formatting.track(it), selected = it.id == state.selectedSubtitleId) }
            )
        PlayerMenu.INFO -> report(state).map { MenuOption(it.text, it.heading) }
        PlayerMenu.SPEED -> {
            val current = PlaybackController.SPEEDS.minBy { abs(it - state.speed) }
            PlaybackController.SPEEDS.map { MenuOption(if (it == 1.0) "Normal" else Formatting.speed(it), selected = it == current) }
        }
    }

    /** Opens [menu] on what is in effect now, or closes it when it is already open. */
    fun toggleMenu(menu: PlayerMenu) {
        touch()
        if (this.menu == menu) this.menu = null else showMenu(menu)
    }

    fun closeMenu() {
        menu = null
    }

    /** The episodes either side of this one, the next one first: it is the one usually wanted. */
    private fun neighbours(): List<Pair<LibraryEntry, Boolean>> = listOfNotNull(next?.let { it to true }, previous?.let { it to false })

    private fun episodeLabel(entry: LibraryEntry): String = (entry.item as? Episode)?.let { "${Formatting.episodeNumber(it)} · ${it.title}" } ?: entry.item.title

    private fun showMenu(menu: PlayerMenu) {
        highlighted = options(menu).indexOfFirst { it.selected }.coerceAtLeast(0)
        this.menu = menu
    }

    /** Puts option [index] of [menu] into effect and closes the menu. */
    fun choose(menu: PlayerMenu, index: Int) {
        val state = player.state.value
        when (menu) {
            PlayerMenu.EPISODES -> neighbours().getOrNull(index)?.let { (_, forward) -> if (forward) playNext() else playPrevious() }
            PlayerMenu.CHAPTERS -> state.chapters.getOrNull(index)?.let { seekTo(it.startMs) }
            PlayerMenu.AUDIO -> {
                val inFile = fileAudio()
                if (inFile != null) inFile.getOrNull(index)?.let { chooseInFile(AudioChoice.Explicit(index), null) } else state.audioTracks.getOrNull(index)?.let { selectAudio(it.id) }
            }
            PlayerMenu.SUBTITLES -> {
                val inFile = fileSubtitles()
                when {
                    inFile == null -> if (index == 0) selectSubtitle(null) else state.subtitleTracks.getOrNull(index - 1)?.let { selectSubtitle(it.id) }
                    index == 0 -> chooseInFile(null, SubtitleChoice.Off)
                    else -> inFile.getOrNull(index - 1)?.let { chooseInFile(null, SubtitleChoice.Explicit(it.position!!, it.external)) }
                }
            }
            PlayerMenu.SPEED -> PlaybackController.SPEEDS.getOrNull(index)?.let(::setSpeed)
            // Read, not chosen.
            PlayerMenu.INFO -> Unit
        }
        this.menu = null
    }

    // Direct controls ----------------------------------------------------------------------------

    fun seekTo(positionMs: Long) {
        touch()
        cancelUpNext()
        val target = positionMs.coerceAtLeast(0)
        if (player.state.value.seekable == false) seekRefused(target) else player.seekTo(target)
    }

    fun skip(segment: SkipSegment) = seekTo(segment.endMs)

    fun setVolume(volume: Int) {
        touch()
        player.setVolume(volume)
        if (volume > 0) player.setMuted(false)
    }

    fun setSpeed(speed: Double) {
        touch()
        player.setSpeed(speed)
    }

    /** Switches the audio track. A track the engine no longer offers is refused with a word, not silently. */
    fun selectAudio(trackId: String) {
        touch()
        val track = player.state.value.audioTracks.firstOrNull { it.id == trackId } ?: return app.notify(Problems.TRACK_GONE)
        chosenAudio = track.position
        player.selectAudio(trackId)
    }

    /** Selects a subtitle track, or turns subtitles off with null. */
    fun selectSubtitle(trackId: String?) {
        touch()
        if (trackId != null && player.state.value.subtitleTracks.none { it.id == trackId }) return app.notify(Problems.TRACK_GONE)
        player.selectSubtitle(trackId)
    }

    /** How much of the bottom of the window (0–1) the controls cover; subtitles are kept above it. */
    var subtitleClearance by mutableFloatStateOf(0f)
        private set

    /** Keeps subtitles above the bottom [fraction] of the window while the controls show there; 0 when they do not. */
    fun keepSubtitlesClear(fraction: Float) {
        val clearance = fraction.coerceIn(0f, 1f)
        if (clearance == subtitleClearance) return
        subtitleClearance = clearance
        player.setSubtitleClearance(clearance)
    }

    fun playNext() = switchTo(next)

    fun playPrevious() = switchTo(previous)

    /** Dismisses the post-play prompt and stays on the finished episode. */
    fun cancelUpNext() {
        countdown?.cancel()
        upNext = null
    }

    /** Answers "still watching?" with yes. */
    fun continueWatching() {
        autoplay.onStillWatching()
        playNext()
    }

    /** Returns once every position reported so far has been written to the library. */
    suspend fun recorded() {
        reporter?.flush()
    }

    /**
     * Leaves the player: records the position, releases the engine, and returns to browsing. The player UI
     * disappears at once; the returned job completes when the position is saved and the engine released.
     */
    fun close(): Job {
        closing?.let { return it }
        countdown?.cancel()
        rememberVolume()
        controller?.stop()
        scope.cancel()
        if (app.platform.fullscreen != wasFullscreen) app.platform.requestFullscreen(wasFullscreen)
        val pending = reporter
        val stream = current
        val release = app.scope.launch(start = CoroutineStart.LAZY) {
            try {
                pending?.flush()
                app.invalidate()
                (stream?.source as? NegotiatingSource)?.let { withContext(app.background) { it.release(stream.version.version.location.path) } }
            } finally {
                try {
                    app.platform.releasePlayer(player)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.platform.report("Releasing the playback engine failed", e)
                }
            }
        }
        closing = release
        app.playbackClosed(this, release)
        release.start()
        return release
    }

    private fun switchTo(target: LibraryEntry?): Boolean {
        if (target == null || closed) return false
        replan(target.item.id, "Moving to another episode failed", "That episode couldn't be played right now.")
        return true
    }

    /**
     * The same film, carrying on: this is the viewer's place now, not progress to come back to. The rules for that
     * would drop the first minute, and doubt a place on a stream that runs a little longer than its file.
     */
    private fun whereTheViewerIs(): Long? = player.state.value.positionMs.takeIf { it > 0 }

    /** Plans [itemId] as Play does and plays it here; when nothing can be played, says why and leaves. */
    private fun replan(
        itemId: MediaId,
        failure: String,
        notice: String,
        continuing: Boolean = false,
        version: VersionId? = null,
        audio: AudioChoice = AudioChoice.Automatic,
        subtitle: SubtitleChoice = SubtitleChoice.Automatic,
        originalFailed: Set<PlanPart> = emptySet(),
        from: Long? = null,
    ) {
        countdown?.cancel()
        upNext = null
        scope.launch {
            try {
                // What was recorded so far is written before the plan reads the work's progress.
                reporter?.flush()
                releaseStream()
                val plan = app.read {
                    planPlayback(itemId, app.platform.device, app.sources, versionId = version, continuing = continuing, preferences = app.viewer, audio = audio, subtitle = subtitle, originalFailed = originalFailed)
                }
                if (plan == null) {
                    app.notify(app.read { unplayableReason(itemId, app.platform.device, app.sources, app.viewer) })
                    close()
                } else {
                    load(if (from == null) plan else plan.copy(request = plan.request.copy(startPositionMs = from)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.platform.report(failure, e)
                app.notify(notice)
                close()
            }
        }
    }

    private fun onState(state: PlayerState) {
        if (state.status == PlayerStatus.PLAYING || state.status == PlayerStatus.PAUSED) {
            begun = true
            // Data came back: a wait that had become a failure was only a wait after all.
            if (waitedTooLong) {
                waitedTooLong = false
                error = null
            }
        }
        watchWaiting(state.status)
        watchSilence(state)
        watchSubtitles(state)
        watchPlace(state)
        if (state.status == PlayerStatus.PLAYING) askForEvidence()
        when (state.status) {
            PlayerStatus.ENDED -> if (!endHandled) {
                endHandled = true
                onEnded()
            }
            PlayerStatus.ERROR -> if (error == null) onFailed(state)
            else -> Unit
        }
    }

    /**
     * An engine's own failure, shown when it comes. A source that could not be reached once playback had
     * begun is the source stopping, whether the engine gave up by itself (Media3's read timeout) or the
     * stream ended short: it reads as the waiting limit does and offers the same Try again.
     */
    private fun onFailed(state: PlayerState) {
        if (state.failure == PlaybackFailure.UNSUPPORTED && planAround(PlanPart.VIDEO)) return
        val unreachable = state.failure == PlaybackFailure.UNREACHABLE
        val message = if (unreachable && begun) Problems.SOURCE_STOPPED else Problems.playback(state.failure)
        error = PlaybackError(message, state.error, canRetry = unreachable)
        menu = null
        cancelUpNext()
        app.platform.report("Playback of \"$title\" failed: ${state.failure ?: PlaybackFailure.UNKNOWN} (${state.error ?: "no detail"})")
        // The library may still describe a file that is gone, or a source that is away; checking the source
        // and rescanning it brings that up to date.
        if (unreachable) sourceId?.let(app::checkSource)
    }

    private fun onEnded() {
        menu = null
        val following = next
        when (val decision = autoplay.onEnded(hasNext = following != null)) {
            is AutoplayPolicy.Decision.Countdown -> {
                upNext = UpNext(following!!, decision.countdownMs, decision.countdownMs, asks = false)
                countdown = scope.launch {
                    var remaining = decision.countdownMs
                    while (remaining > 0) {
                        delay(COUNTDOWN_TICK_MS)
                        remaining -= COUNTDOWN_TICK_MS
                        upNext = upNext?.copy(remainingMs = remaining.coerceAtLeast(0))
                    }
                    playNext()
                }
            }
            AutoplayPolicy.Decision.AskStillWatching -> upNext = UpNext(following!!, 0, 0, asks = true)
            AutoplayPolicy.Decision.Stop -> close()
        }
    }

    private fun rememberVolume() {
        val current = player.state.value
        app.volume = current.volume
        app.muted = current.muted
    }

    private class Details(
        val entry: LibraryEntry?,
        val heading: Pair<String, String?>,
        val neighbours: Pair<LibraryEntry?, LibraryEntry?>,
    )

    private companion object {
        const val COUNTDOWN_TICK_MS = 250L
        /** How long the viewer is left waiting, to start or for data, before the wait is a stated failure. */
        const val PATIENCE_MS = 30_000L

        /** How long sound may be missing before it is missing, not still being set up. */
        const val SILENCE_MS = 3_000L

        /** How long after playback starts the source is asked what it did: its first report has arrived by then. */
        const val EVIDENCE_AFTER_MS = 5_000L
        val WAITING = setOf(PlayerStatus.LOADING, PlayerStatus.BUFFERING)

        /** After a failure the player has one or two things left to do: every "go on" takes the first, back the way back. */
        val LEAVING = setOf(SemanticAction.BACK, SemanticAction.SELECT, SemanticAction.PLAY_PAUSE)
    }
}

/** The player headline for a work: title, and a second line for episodes and dated movies. */
internal fun Library.heading(item: MediaItem): Pair<String, String?> = when (item) {
    is Episode -> {
        val show = item(item.showId)?.title ?: item.title
        show to listOfNotNull(Formatting.episodeNumber(item), item.episodeTitle).joinToString(" · ")
    }
    else -> item.title to Formatting.year(item)?.toString()
}

/** The episodes before and after [item] in viewing order; specials are not part of the sequence. */
internal fun Library.neighbours(item: MediaItem): Pair<LibraryEntry?, LibraryEntry?> {
    val episode = item as? Episode ?: return null to null
    val episodes = showDetail(episode.showId)?.seasons.orEmpty().filter { it.season.number != 0 }.flatMap { it.episodes }
    val index = episodes.indexOfFirst { it.item.id == episode.id }
    if (index < 0) return null to null
    return episodes.getOrNull(index - 1) to episodes.getOrNull(index + 1)
}

/**
 * Moves watch reports off the UI thread while keeping their order, so a progress write can never land
 * after the final "stopped" write.
 */
internal class SerialWatchReporter(
    private val delegate: WatchReporter,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
) : WatchReporter {
    private val serial = dispatcher.limitedParallelism(1)

    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        scope.launch(serial) { delegate.progress(itemId, positionMs, durationMs) }
    }

    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        scope.launch(serial) { delegate.stopped(itemId, positionMs, durationMs) }
    }

    /** Suspends until every report so far has been written. */
    suspend fun flush() = withContext(serial) {}
}
