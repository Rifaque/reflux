package dev.reflux.desktop

import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.KeychainSeal
import dev.reflux.desktop.engine.SecretStore
import dev.reflux.library.ConnectResult
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.state.PlayerMenu
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A plan through the real desktop window, on a real server: the planner, the negotiation, libmpv in its surface, and
 * what the server says happened. Desktop decodes nearly everything, so a device without AC-3 stands in for one that
 * needs its audio converted.
 *
 * Runs only on request (`-Preflux.windowTests=true`) with `~/.reflux-live/jellyfin.env` present. The connection is kept
 * in memory, never in the system's credential store, and nothing of the account is printed.
 */
class PlannedPlaybackWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-planned")
    private lateinit var shell: Shell

    private class Memory : SecretStore {
        private val entries = HashMap<String, String>()
        override fun write(name: String, secret: String) {
            entries[name] = secret
        }
        override fun read(name: String) = entries[name]
        override fun delete(name: String) {
            entries.remove(name)
        }
    }

    private fun live(): Map<String, String>? {
        val file = Path.of(System.getProperty("user.home"), ".reflux-live", "jellyfin.env")
        if (!Files.exists(file)) return null
        return Files.readAllLines(file).filter { "=" in it && !it.trimStart().startsWith("#") }.associate {
            it.substringBefore('=').trim() to it.substringAfter('=').trim().trim('"', '\'')
        }
    }

    private val session: PlaybackSession get() = assertNotNull(shell.ui { shell.app.playback })
    private val state: PlayerState get() = shell.ui { shell.app.playback?.state?.value } ?: PlayerState()

    private fun awaitPlayer(what: String, timeoutMs: Long = 90_000, condition: (PlayerState) -> Boolean) =
        shell.await(what, timeoutMs, state = { "Player: ${shell.app.playback?.state?.value?.copy(tracks = emptyList(), chapters = emptyList())}" }) {
            shell.app.playback?.state?.value?.let(condition) == true
        }

    private fun comparison(what: String, condition: (dev.reflux.core.playback.PlaybackComparison) -> Boolean) =
        shell.await(what, 60_000, state = { "Compared: ${shell.app.playback?.comparison?.summary()}" }) {
            shell.app.playback?.comparison?.let(condition) == true
        }

    @Test
    fun aPlanIsCarriedOutInTheRealWindow() {
        assumeTrue("window tests not requested", windowTestsRequested)
        val settings = live()
        assumeTrue("no live server named", settings != null)
        val environment = DesktopEnvironment(
            root.resolve("data"),
            sealFor = { directory, _ -> KeychainSeal(Memory(), directory) },
            deviceFilter = { device ->
                val gone = setOf(AudioCodec.AC3, AudioCodec.EAC3)
                device.copy(audioDecoders = device.audioDecoders - gone, audioPassthrough = device.audioPassthrough - gone, audioPassthroughKnown = true)
            },
        )
        val request = ConnectionRequest(settings!!.getValue("REFLUX_JELLYFIN_TEST"), settings["REFLUX_JELLYFIN_TEST_USER"], settings["REFLUX_JELLYFIN_TEST_PASSWORD"])
        assertIs<ConnectResult.Connected>(runBlocking { environment.connections.connect(ConnectionKind.SERVER, request) })
        runBlocking { environment.pipeline.refresh() }
        val film = environment.library.movies().first { it.item.title == "Mission: Impossible - Fallout" }.item.id
        val subtitles = environment.library.versions(film).first().version.stream.subtitles
        // The last: this server answers 404 for the film's other PGS tracks, which the first load shows being reported.
        val pgs = subtitles.indexOfLast { it.format == SubtitleFormat.PGS && !it.forced }
        assertTrue(pgs >= 0, "the film has PGS")

        shell = Shell(environment)
        try {
            shell.awaitShown("Home", exact = false)
            aPlanIsPlayedAndConfirmedByTheServer(film)
            anotherLanguageCarriesOnFromThePlace()
            aSubtitleTheServerExtractsArrivesWithThePictureRunning(pgs)
            closes()
        } finally {
            shell.close()
            printTheLog()
            root.toFile().deleteRecursively()
        }
    }

    /** Addresses, ids, and tokens taken out: the log is redacted, and this test's output is kept. */
    private fun printTheLog() {
        val log = root.resolve("data/reflux.log")
        if (!Files.exists(log)) return
        Files.readAllLines(log).takeLast(40).forEach { line ->
            println("LOG " + line.replace(Regex("https?://[^ ]+"), "<address>").replace(Regex("[0-9a-fA-F]{32}"), "<id>").take(240))
        }
    }

    /**
     * How the player goes away, one way per process, since what this looks for ends the process: `REFLUX_TEARDOWN` is
     * `<hls|file>-<quick|wait|window>`. On libmpv 0.37 the engine has been seen to assert in its X11 code after a run.
     */
    @Test
    fun aPlayerGoesAwayCleanly() {
        val mode = System.getenv("REFLUX_TEARDOWN")
        assumeTrue("not asked for", mode != null && windowTestsRequested)
        val settings = assertNotNull(live(), "no live server named")
        val environment = DesktopEnvironment(
            root.resolve("data"),
            sealFor = { directory, _ -> KeychainSeal(Memory(), directory) },
            deviceFilter = { device ->
                val gone = if (mode!!.startsWith("hls")) setOf(AudioCodec.AC3, AudioCodec.EAC3) else emptySet()
                device.copy(audioDecoders = device.audioDecoders - gone, audioPassthrough = device.audioPassthrough - gone, audioPassthroughKnown = true)
            },
        )
        val request = ConnectionRequest(settings.getValue("REFLUX_JELLYFIN_TEST"), settings["REFLUX_JELLYFIN_TEST_USER"], settings["REFLUX_JELLYFIN_TEST_PASSWORD"])
        assertIs<ConnectResult.Connected>(runBlocking { environment.connections.connect(ConnectionKind.SERVER, request) })
        runBlocking { environment.pipeline.refresh() }
        val film = environment.library.movies().first { it.item.title == "Mission: Impossible - Fallout" }.item.id
        shell = Shell(environment)
        try {
            shell.awaitShown("Home", exact = false)
            shell.ui { shell.app.play(film, resume = false) }
            awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 }
            Thread.sleep(4_000)
            println("TEARDOWN $mode playing, ${shell.ui { session.comparison?.planned?.delivery }}")
            if (!mode!!.endsWith("window")) {
                shell.ui { session.close() }
                shell.await("the player to close", 20_000) { shell.app.playback == null }
            }
            if (mode.endsWith("wait")) Thread.sleep(6_000)
        } finally {
            shell.close()
            root.toFile().deleteRecursively()
        }
        println("TEARDOWN $mode closed")
    }

    private fun aPlanIsPlayedAndConfirmedByTheServer(film: MediaId) {
        shell.ui { shell.app.play(film, resume = false) }
        awaitPlayer("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 1_000 }
        shell.ui { session.seekTo(300_000) }
        awaitPlayer("the seek") { it.status == PlayerStatus.PLAYING && it.positionMs in 300_500L..340_000L }
        comparison("the comparison") { true }
        val compared = assertNotNull(shell.ui { session.comparison })
        println("PLANNED ${compared.summary()}")
        assertEquals(DeliveryMode.AUDIO_CONVERSION, compared.planned.delivery)
        assertEquals(emptySet(), compared.mismatches, compared.summary())
        shell.capture("planned-audio-conversion")
    }

    private fun anotherLanguageCarriesOnFromThePlace() {
        val options = shell.ui { session.options(PlayerMenu.AUDIO) }
        println("AUDIO ${options.map { it.label to it.selected }}")
        val other = options.indexOfFirst { !it.selected }
        val before = state.positionMs
        shell.ui { session.choose(PlayerMenu.AUDIO, other) }
        comparison("the other language to be planned") { it.planned.audioIndex == other }
        awaitPlayer("the other language to play") { it.status == PlayerStatus.PLAYING }
        val after = state.positionMs
        println("PLACE before $before after $after; ${shell.ui { session.comparison?.summary() }}")
        assertTrue(after in (before - 5_000)..(before + 60_000), "from $before, at $after")
        assertEquals(emptySet(), shell.ui { session.comparison?.mismatches })
    }

    private fun aSubtitleTheServerExtractsArrivesWithThePictureRunning(track: Int) {
        val started = System.currentTimeMillis()
        shell.ui { session.choose(PlayerMenu.SUBTITLES, track + 1) }
        // Another subtitle is another stream: wait for the one before it to be gone, and with it what it reported.
        shell.await("the stream to change", 20_000) { shell.app.playback?.comparison == null }
        comparison("the subtitle to be planned") { it.planned.subtitles != SubtitlePlan.NONE }
        val planned = assertNotNull(shell.ui { session.comparison })
        println("SUBTITLE ${planned.summary()}")
        assertEquals(SubtitlePlan.EXTERNAL, planned.planned.subtitles)
        val picture = System.currentTimeMillis() - started
        awaitPlayer("the extracted subtitle", 240_000) { state -> state.subtitleTracks.any { it.external } || state.subtitlesNotLoaded > 0 }
        val arrived = System.currentTimeMillis() - started
        println("EXTRACTION picture by ${picture / 1000} s, subtitle by ${arrived / 1000} s, not loaded ${state.subtitlesNotLoaded}")
        assertEquals(0, state.subtitlesNotLoaded)
        awaitPlayer("the subtitle to be selected") { it.selectedSubtitleId != null && it.status == PlayerStatus.PLAYING }
        repeat(14) { index ->
            shell.capture("pgs-in-mpv-$index")
            Thread.sleep(1_500)
        }
    }

    private fun closes() {
        val at = state.positionMs
        shell.ui { session.close() }
        shell.await("the player to close", 20_000) { shell.app.playback == null }
        println("CLOSED at $at")
    }
}
