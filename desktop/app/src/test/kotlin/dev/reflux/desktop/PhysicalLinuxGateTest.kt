package dev.reflux.desktop

import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleFormat
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
 * The physical Linux gate of M2P.4 (docs/VERIFICATION.md). Run by `tools/live/physical-linux-gate.sh`, on request only.
 *
 * Asked for anywhere that is not a real Linux machine, it fails rather than skips: a container, WSL, a virtual display,
 * or a machine without a sound card or a render node proves nothing, and must not look as though it does.
 */
class PhysicalLinuxGateTest {
    private val root: Path = Files.createTempDirectory("reflux-gate")
    private lateinit var shell: Shell

    /** Stands in for a device that cannot decode AC-3: libmpv decodes nearly everything itself. */
    @Volatile private var withoutAc3 = false

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

    private fun read(path: String): String = runCatching { Files.readString(Path.of(path)) }.getOrDefault("")

    /** Why this is not physical Linux hardware; empty when it is. */
    private fun notPhysical(): List<String> = buildList {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            add("not Linux")
            return@buildList
        }
        if (Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/run/.containerenv"))) add("a container")
        if (Regex("docker|containerd|kubepods|lxc").containsMatchIn(read("/proc/1/cgroup"))) add("a container")
        if ("microsoft" in read("/proc/version").lowercase()) add("WSL")
        if (Regex("hypervisor").containsMatchIn(read("/proc/cpuinfo"))) add("a virtual machine")
        if (ProcessBuilder("pgrep", "-x", "Xvfb").start().waitFor() == 0) add("a virtual display (Xvfb)")
        if (System.getenv("DISPLAY").isNullOrBlank() && System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) add("no display")
        val cards = read("/proc/asound/cards")
        if (cards.isBlank() || "no soundcards" in cards) add("no sound card")
        val render = Path.of("/dev/dri/renderD128")
        if (!Files.isReadable(render)) add("no readable render node")
    }

    private fun live(): Map<String, String> {
        val file = Path.of(System.getProperty("user.home"), ".reflux-live", "jellyfin.env")
        assertTrue(Files.exists(file), "no live server named")
        return Files.readAllLines(file).filter { "=" in it && !it.trimStart().startsWith("#") }.associate {
            it.substringBefore('=').trim() to it.substringAfter('=').trim().trim('"', '\'')
        }
    }

    private val session: PlaybackSession get() = assertNotNull(shell.ui { shell.app.playback })
    private val state: PlayerState get() = shell.ui { shell.app.playback?.state?.value } ?: PlayerState()

    private fun awaitPlayer(what: String, timeoutMs: Long = 90_000, condition: (PlayerState) -> Boolean) =
        shell.await(what, timeoutMs, state = { "Status: ${shell.app.playback?.state?.value?.status}, failure ${shell.app.playback?.state?.value?.failure}" }) {
            shell.app.playback?.state?.value?.let(condition) == true
        }

    private fun comparison(what: String, condition: (PlaybackComparison) -> Boolean = { true }): PlaybackComparison {
        shell.await(what, 60_000, state = { "Compared: ${shell.app.playback?.comparison?.summary()}" }) {
            shell.app.playback?.comparison?.let(condition) == true
        }
        return assertNotNull(shell.ui { session.comparison })
    }

    private fun closePlayer() {
        shell.ui { session.close() }
        shell.await("the player to close", 20_000) { shell.app.playback == null }
    }

    private fun play(film: MediaId, resume: Boolean = false, what: String) {
        shell.ui { shell.app.play(film, resume = resume) }
        awaitPlayer("$what to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 500 }
    }

    @Test
    fun theGate() {
        assumeTrue("not asked for", System.getenv("REFLUX_PHYSICAL_GATE") == "1")
        val refused = notPhysical()
        assertTrue(refused.isEmpty(), "This is not physical Linux hardware: $refused")
        val settings = live()
        val environment = DesktopEnvironment(
            root.resolve("data"),
            sealFor = { directory, _ -> KeychainSeal(Memory(), directory) },
            deviceFilter = { device ->
                val gone = if (withoutAc3) setOf(AudioCodec.AC3, AudioCodec.EAC3) else emptySet()
                device.copy(audioDecoders = device.audioDecoders - gone, audioPassthrough = device.audioPassthrough - gone, audioPassthroughKnown = true)
            },
        )
        val request = ConnectionRequest(settings.getValue("REFLUX_JELLYFIN_TEST"), settings["REFLUX_JELLYFIN_TEST_USER"], settings["REFLUX_JELLYFIN_TEST_PASSWORD"])
        assertIs<ConnectResult.Connected>(runBlocking { environment.connections.connect(ConnectionKind.SERVER, request) })
        runBlocking { environment.pipeline.refresh() }
        fun film(title: String) = environment.library.movies().first { it.item.title == title }.item.id
        val direct = film("2 Fast 2 Furious")
        val languages = film("Mickey 17")
        val pgsFilm = film("Mission: Impossible - Fallout")
        val pgs = environment.library.versions(pgsFilm).first().version.stream.subtitles.indexOfLast { it.format == SubtitleFormat.PGS && !it.forced }

        shell = Shell(environment)
        try {
            // 1. It launches.
            shell.awaitShown("Home", exact = false)
            println("GATE 1 launched")

            // 2, 4, 9. Direct Play, on a hardware decoder, planned against actual.
            play(direct, what = "Direct Play")
            val original = comparison("the Direct Play comparison")
            println("GATE 4 ${original.summary()}")
            assertEquals(DeliveryMode.DIRECT_PLAY, original.planned.delivery)
            assertEquals(emptySet(), original.mismatches, original.summary())
            assertEquals(true, original.decoder?.hardware, "a hardware decoder, not ${original.decoder?.name}")
            println("GATE 2 decoder ${original.decoder?.name}")
            shell.capture("gate-direct-play")

            // 7. Seek and resume.
            shell.ui { session.seekTo(300_000) }
            awaitPlayer("the seek") { it.status == PlayerStatus.PLAYING && it.positionMs in 300_500L..340_000L }
            val left = state.positionMs
            closePlayer()
            play(direct, resume = true, what = "the resume")
            val resumed = state.positionMs
            println("GATE 7 left at $left, resumed at $resumed")
            assertTrue(resumed in (left - 30_000)..(left + 60_000), "left at $left, resumed at $resumed")
            closePlayer()

            // 5, 9. Audio conversion, and an explicit language from the same place.
            withoutAc3 = true
            play(languages, what = "audio conversion")
            val converted = comparison("the conversion comparison")
            println("GATE 5 ${converted.summary()}")
            assertEquals(DeliveryMode.AUDIO_CONVERSION, converted.planned.delivery)
            assertEquals(emptySet(), converted.mismatches, converted.summary())
            closePlayer()

            // 6. A subtitle the server extracts, on screen.
            play(pgsFilm, what = "the subtitle film")
            shell.ui { session.seekTo(300_000) }
            awaitPlayer("the seek") { it.status == PlayerStatus.PLAYING && it.positionMs >= 300_000 }
            comparison("the comparison")
            shell.ui { session.choose(PlayerMenu.SUBTITLES, pgs + 1) }
            shell.await("the stream to change", 20_000) { shell.app.playback?.comparison == null }
            comparison("the subtitle plan")
            awaitPlayer("the extracted subtitle", 240_000) { s -> s.subtitleTracks.any { it.external } || s.subtitlesNotLoaded > 0 }
            assertEquals(0, state.subtitlesNotLoaded)
            awaitPlayer("the subtitle to be selected") { it.selectedSubtitleId != null && it.status == PlayerStatus.PLAYING }
            repeat(14) { index ->
                shell.capture("gate-subtitle-$index")
                Thread.sleep(1_500)
            }
            println("GATE 6 subtitle selected, frames saved where -Preflux.screenshots asked")
            closePlayer()
        } finally {
            shell.close()
            noCredentialIn(root.resolve("data/reflux.log"), settings)
            root.toFile().deleteRecursively()
        }
        // 8. Reaching here means leaving did not abort the process.
        println("GATE 8 left cleanly")
    }

    /** By comparison only: what it looks for is never printed. */
    private fun noCredentialIn(log: Path, settings: Map<String, String>) {
        val text = if (Files.exists(log)) Files.readString(log) else ""
        val password = settings["REFLUX_JELLYFIN_TEST_PASSWORD"].orEmpty()
        assertTrue(password.isEmpty() || password !in text, "the password is in the log")
        assertTrue(!Regex("""Token="?[0-9a-fA-F]{32}""").containsMatchIn(text), "a token is in the log")
        println("GATE 10 log clean")
    }
}
