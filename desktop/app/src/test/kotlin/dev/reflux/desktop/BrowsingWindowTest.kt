package dev.reflux.desktop

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.FolderAddition
import dev.reflux.desktop.gamepad.GamepadReader
import dev.reflux.desktop.gamepad.GamepadState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Browsing in the real window: with a mouse, and with a controller read through the shell's real input
 * path (reader, translator, UI thread, semantic actions, toolkit focus).
 *
 * It opens windows, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`.
 */
class BrowsingWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-browsing")

    private val movies = listOf(
        "Alien (1979)", "Arrival (2016)", "Dune (2021)", "Heat (1995)", "Interstellar (2014)", "Parasite (2019)",
        "The Matrix (1999)", "Whiplash (2014)", "Night Drive (2021)", "The Long Road (1998)", "Paper Moon (1973)", "Solaris (1972)",
    )

    private fun environment(): DesktopEnvironment {
        val media = root.resolve("Media")
        fun file(relative: String) = media.resolve(relative).also { it.parent.createDirectories() }.writeBytes(ByteArray(8))
        movies.forEach { file("$it/$it.mkv") }
        for (episode in 1..3) file("Harbour Lights/Season 1/Harbour.Lights.S01E0$episode.mkv")
        return DesktopEnvironment(root.resolve("data")).also { environment ->
            val added = environment.addLocalFolder(media) as FolderAddition.Added
            runBlocking { environment.library.scan(added.source) }
        }
    }

    @Test
    fun theMouseBrowsesTheRealWindow() {
        assumeTrue("window tests not requested", windowTestsRequested)
        try {
            Shell(environment()).use { shell ->
                val app = shell.app
                shell.awaitShown("Recently Added")
                // On X11 the engine must not be able to take the toolkit's error handler away (see X11ErrorGuard).
                if (X11ErrorGuard.needed) assertNotNull(X11ErrorGuard.create(), "the X11 error guard is in place")

                // Hovering a row offers to page it; paging moves it.
                val hidden = movies.map { it.replace(" (", ", ").removeSuffix(")") }.filterNot { shell.shows(it) }
                assertTrue(hidden.isNotEmpty(), "the row is longer than the window is wide")
                shell.clickRevealedBy("Recently Added", belowDp = 150f, target = "More of Recently Added")
                shell.await("the row to page") { hidden.any { shell.shows(it) } }

                // The wheel scrolls the page.
                val before = shell.boundsOf("Recently Added").top
                shell.wheel("Recently Added", 2)
                shell.await("the page to scroll") { shell.boundsOf("Recently Added").top < before - 40f }

                // Navigation, details, and the ways back.
                shell.click("Movies")
                shell.awaitShown("12 titles")
                shell.click("Solaris, 1972")
                shell.awaitShown("Version")
                assertTrue(shell.ui { app.navigator.current } is Destination.Details)
                shell.click("Add to favorites")
                shell.awaitShown("Remove from favorites")
                shell.key(KeyEvent.VK_ESCAPE)
                shell.awaitShown("12 titles")
                assertEquals(Destination.Movies, shell.ui { app.navigator.current })
                shell.click("Solaris, 1972")
                shell.awaitShown("Version")
                shell.click("Back")
                shell.awaitShown("12 titles")

                // Search: a suggestion, results, and clearing.
                shell.click("Search")
                shell.click("favorites")
                shell.awaitShown("Solaris, 1972")
                assertFalse(shell.shows("Heat, 1995"))
                shell.click("Clear search")
                shell.awaitShown("Try")
                assertEquals("", shell.ui { app.searchQuery })

                // The sources are a page of their own, and the same button leaves it.
                shell.click("Media sources")
                shell.awaitShown("Add media")
                shell.capture("window-sources")
                shell.click("Add media")
                shell.awaitShown("A folder on this device")
                shell.capture("window-add-media")
                shell.click("Network share")
                shell.awaitShown("Add a network share")
                shell.capture("window-connect")
                shell.click("Cancel")
                shell.await("the choice again") { app.navigator.current == Destination.AddSource }
                shell.click("Back")
                shell.await("the sources again") { app.navigator.current == Destination.Sources }
                // The page's title says "Media sources" too; Back is the unambiguous way out.
                shell.click("Back")
                shell.await("the page to be left") { app.navigator.current == Destination.Search }

                shell.click("Home")
                shell.awaitShown("Recently Added")
                assertEquals(InputModality.POINTER, shell.ui { app.input.modality })
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    /** A controller whose buttons the test presses. Everything downstream of the reader is the real shell. */
    private class ScriptedGamepad(private val family: ControllerFamily) : GamepadReader {
        @Volatile private var state = GamepadState("scripted", family, emptySet())

        override fun poll(): List<GamepadState> = listOf(state)

        fun press(button: GamepadButton) {
            state = state.copy(buttons = setOf(button))
            Thread.sleep(70)
            state = state.copy(buttons = emptySet())
            Thread.sleep(70)
        }

        fun stick(x: Float, y: Float, holdMs: Long = 70) {
            state = state.copy(stickX = x, stickY = y)
            Thread.sleep(holdMs)
            state = state.copy(stickX = 0f, stickY = 0f)
            Thread.sleep(70)
        }
    }

    @Test
    fun aControllerBrowsesTheRealWindow() {
        assumeTrue("window tests not requested", windowTestsRequested)
        val pad = ScriptedGamepad(ControllerFamily.PLAYSTATION)
        try {
            val environment = environment()
            Shell(environment, pad).use { shell ->
                val app = shell.app
                shell.awaitShown("Recently Added")
                // Controllers act only on the application in use; the test window has to be that application.
                shell.ui { shell.window.requestFocus() }
                val active = runCatching { shell.await("the window to become active", 5_000) { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null } }.isSuccess
                assumeTrue("the test window could not become the active window", needs("the test window as the active window", active))

                // The first press morphs the layout and puts focus on the main action; no mode was switched.
                assertFalse(shell.shows("Cross"))
                pad.press(GamepadButton.DPAD_DOWN)
                shell.await("the layout to morph") { app.input.modality == InputModality.DIRECTIONAL }
                assertEquals(ControllerFamily.PLAYSTATION, shell.ui { app.input.controllerFamily })
                shell.awaitShown("Cross")
                shell.awaitShown("Options")
                assertEquals(Destination.Home, shell.ui { app.navigator.current })
                Thread.sleep(500)
                shell.capture("window-controller")

                // Down to the first row, along it with the stick, and into a title.
                pad.press(GamepadButton.DPAD_DOWN)
                pad.stick(1f, 0f)
                pad.press(GamepadButton.FACE_SOUTH)
                shell.await("details to open") { app.navigator.current is Destination.Details }
                shell.awaitShown("Circle")
                pad.press(GamepadButton.FACE_EAST)
                shell.await("Back to return Home") { app.navigator.current == Destination.Home }

                // Holding a direction travels. What the machine was found to decode refreshes pages once, a few
                // seconds after start; that is not navigation.
                shell.await("the decode probe to finish", timeoutMs = 30_000) { environment.capabilities.probeFinished }
                pad.press(GamepadButton.DPAD_DOWN)
                pad.press(GamepadButton.DPAD_DOWN)
                val start = shell.ui { app.revision }
                pad.stick(1f, 0f, holdMs = 1_200)
                assertEquals(start, shell.ui { app.revision }, "navigation alone changes nothing in the library")

                // Options opens the sources page; Circle goes back.
                pad.press(GamepadButton.START)
                shell.await("the sources page") { app.navigator.current == Destination.Sources }
                pad.press(GamepadButton.FACE_EAST)
                shell.await("back from the sources page") { app.navigator.current == Destination.Home }

                // Create toggles fullscreen, in the real window.
                pad.press(GamepadButton.SELECT)
                shell.await("fullscreen") { app.platform.fullscreen }
                pad.press(GamepadButton.SELECT)
                shell.await("windowed") { !app.platform.fullscreen }

                // The pointer takes over again just by being used.
                shell.click("Movies")
                shell.await("pointer mode") { app.input.modality == InputModality.POINTER }
                shell.awaitGone("Cross")
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
