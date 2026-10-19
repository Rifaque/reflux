package dev.reflux.desktop

import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.FolderAddition
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The About page and the licenses behind it in the real window: reached with the mouse, read with the keyboard,
 * and left with Escape back to where each page was left.
 *
 * It opens a window, so it only runs on request: `./gradlew :desktop:app:test -Preflux.windowTests=true`.
 */
class AboutWindowTest {
    private val root: Path = Files.createTempDirectory("reflux-about")

    private fun environment(): DesktopEnvironment {
        val media = root.resolve("Media")
        media.resolve("Heat (1995)/Heat (1995).mkv").also { it.parent.createDirectories() }.writeBytes(ByteArray(8))
        return DesktopEnvironment(root.resolve("data")).also { environment ->
            val added = environment.addLocalFolder(media) as FolderAddition.Added
            runBlocking { environment.library.scan(added.source) }
        }
    }

    @Test
    fun aboutAndTheLicensesWorkInTheRealWindow() {
        assumeTrue("window tests not requested", windowTestsRequested)
        try {
            Shell(environment()).use { shell ->
                shell.awaitShown("Media sources")
                shell.click("Media sources")
                shell.click("About Reflux")
                // The build this window is: the version and the commit its build recorded.
                shell.awaitShown("Version ${readBuildIdentity().label}")
                shell.awaitShown("Privacy")
                shell.capture("about")

                // The keyboard reads the page a stop at a time and goes on to the notices.
                shell.key(KeyEvent.VK_DOWN)
                shell.await("focus on the build", state = { "focused: ${shell.focused()}" }) { shell.focused() == "Reflux" }
                shell.key(KeyEvent.VK_DOWN)
                shell.key(KeyEvent.VK_DOWN)
                shell.await("focus on the notices", state = { "focused: ${shell.focused()}" }) { shell.focused() == "Third-party software" }
                shell.key(KeyEvent.VK_ENTER)
                shell.awaitShown("Kotlin and kotlinx libraries")
                // This is the desktop's list (what the desktop package contains); the click scrolls to it as a wheel would.
                shell.click("Skia")
                shell.awaitShown("BSD-3-Clause")
                shell.awaitShown("Copyright (c) 2011 Google Inc. All rights reserved.", exact = false)
                shell.capture("notice")

                // Escape goes back a page at a time. The license was opened with the mouse, so no focus shows until
                // the first arrow, and that arrow lands on what each page was left by, not at its top.
                shell.key(KeyEvent.VK_ESCAPE)
                shell.awaitShown("Third-party software")
                shell.key(KeyEvent.VK_ESCAPE)
                shell.awaitShown("Privacy")
                shell.key(KeyEvent.VK_DOWN)
                shell.await("focus back on the notices row", state = { "focused: ${shell.focused()}" }) { shell.focused() == "Third-party software" }
                shell.key(KeyEvent.VK_ESCAPE)
                shell.await("the media sources") { shell.app.navigator.current == Destination.Sources }
                shell.await("focus back on About", state = { "focused: ${shell.focused()}" }) { shell.focused() == "About Reflux" }
                assertEquals(Destination.Sources, shell.ui { shell.app.navigator.current })
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
