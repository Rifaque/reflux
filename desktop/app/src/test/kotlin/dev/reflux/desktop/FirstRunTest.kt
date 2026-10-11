package dev.reflux.desktop

import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.ui.state.Destination
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first run, end to end, in the real window with the platform's real folder dialog: launch, choose a
 * folder, and arrive at a populated Home; then launch again and find everything still there.
 *
 * It opens windows, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`.
 */
class FirstRunTest {
    private val root: Path = Files.createTempDirectory("reflux-first-run")
    private val media: Path = root.resolve("My Media")
    private val data: Path = root.resolve("data")

    private fun file(relative: String) = media.resolve(relative).also { it.parent.createDirectories() }.writeBytes(ByteArray(8))

    @Test
    fun choosingAFolderBuildsTheLibraryAndTheNextLaunchRemembersIt() {
        assumeTrue("window tests not requested", windowTestsRequested)
        file("Night Drive (2021)/Night.Drive.2021.1080p.mkv")
        file("The.Long.Road.1998.720p.BluRay.x264-GROUP.mkv")
        for (episode in 1..3) file("Harbour Lights/Season 1/Harbour.Lights.S01E0$episode.mkv")

        try {
            firstLaunch()
            secondLaunch()
            launchWithoutTheFolder()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun firstLaunch() {
        val environment = DesktopEnvironment(data)
        Shell(environment).use { shell ->
            shell.awaitShown("Point Reflux at your media")
            shell.capture("first-run-window")
            assertTrue(environment.library.sources().isEmpty())

            // Closing the dialog is not an error and changes nothing.
            shell.click("Choose a folder")
            NativeDialogs.answer(FolderPicker.TITLE, folder = null)
            shell.await("the dialog to close") { !NativeDialogs.isOpen(FolderPicker.TITLE) }
            Thread.sleep(500)
            assertTrue(environment.library.sources().isEmpty())
            assertNull(shell.ui { shell.app.notice })
            assertTrue(shell.shows("Point Reflux at your media"))

            // Choosing a folder is all it takes: Reflux scans it and Home appears.
            shell.click("Choose a folder")
            NativeDialogs.answer(FolderPicker.TITLE, media)
            shell.awaitShown("Recently Added")
            val source = environment.library.sources().single()
            assertEquals("My Media", source.displayName)
            assertEquals(media.toRealPath(), Path.of(source.config).toRealPath())
            assertEquals(Destination.Home, shell.ui { shell.app.navigator.current })
            shell.await("the library to be complete") {
                environment.library.movies().map { it.item.title }.toSet() == setOf("Night Drive", "The Long Road") &&
                    environment.library.shows().map { it.item.title } == listOf("Harbour Lights")
            }
            assertFalse(shell.shows("Point Reflux at your media"))
            shell.capture("first-run-library")

            // Choosing the same folder again says so instead of adding it twice.
            shell.click("Media sources")
            shell.click("Add folder")
            NativeDialogs.answer(FolderPicker.TITLE, media)
            shell.await("the duplicate to be explained") { shell.app.notice?.contains("already in your library") == true }
            assertEquals(1, environment.library.sources().size)
        }
    }

    private fun secondLaunch() {
        val environment = DesktopEnvironment(data)
        assertEquals(listOf("My Media"), environment.library.sources().map { it.displayName })
        Shell(environment).use { shell ->
            shell.awaitShown("Recently Added")
            assertFalse(shell.shows("Choose a folder"), "a second launch does not ask again")
            assertEquals(3, environment.library.movies().size + environment.library.shows().size)
        }
    }

    /** The folder went away between launches: the library is still browsable and says what happened. */
    private fun launchWithoutTheFolder() {
        Files.move(media, root.resolve("Moved"))
        val environment = DesktopEnvironment(data)
        Shell(environment).use { shell ->
            shell.awaitShown("Recently Added")
            shell.click("Media sources")
            shell.awaitShown("Not connected", exact = false)
            assertEquals(3, environment.library.movies().size + environment.library.shows().size, "the library is still there")
            shell.awaitShown("Offline")
            shell.capture("first-run-source-missing")

            // Something that cannot be played says why instead of doing nothing.
            shell.clickSurface(0.3f, 0.9f) // closes the panel
            shell.ui { shell.app.play(environment.library.movies().first().item.id) }
            shell.await("the offline source to be named") { shell.app.notice?.contains("My Media is offline") == true }
        }
    }
}
