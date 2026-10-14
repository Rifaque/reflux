package dev.reflux.desktop.gamepad

import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.InputModality
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.desktop.SampleMedia
import dev.reflux.desktop.Shell
import dev.reflux.desktop.engine.DesktopEnvironment
import dev.reflux.desktop.engine.FolderAddition
import dev.reflux.desktop.windowTestsRequested
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A controller in someone's hands and the real application: the one part of controller support that no
 * script can stand in for. Everything from the platform's reader to the screen is the real thing.
 *
 * Connect a controller, then run
 * `./gradlew :desktop:app:test --tests "*PhysicalControllerTest*" -Preflux.windowTests=true -Preflux.controller=true -i`
 * and do what the lines starting with `CONTROLLER:` ask. Each step waits a minute.
 */
class PhysicalControllerTest {
    private fun say(text: String) = println("CONTROLLER: $text")

    @Test
    fun aControllerInHandDrivesTheRealWindow() {
        assumeTrue("not requested", windowTestsRequested && System.getProperty("reflux.controller") == "true")
        val reader = GamepadInput.platformReader()
        assumeTrue("no controller reader on this platform", reader != null)
        // Devices are found on the first polls; give a controller that was just switched on a moment.
        val found = (1..40).firstNotNullOfOrNull { reader!!.poll().firstOrNull() ?: null.also { Thread.sleep(250) } }
        assumeTrue("no controller is connected", found != null)
        val family = found!!.family
        fun name(button: GamepadButton) = InputMapping.glyph(button, family)
        val confirm = name(InputMapping.confirmButton(family))
        val cancel = name(InputMapping.cancelButton(family))
        say("found ${found.id}, read as a $family controller")

        val root = Files.createTempDirectory("reflux-controller")
        val media = root.resolve("Media")
        for (title in listOf("Alien (1979)", "Arrival (2016)", "Dune (2021)", "Heat (1995)", "Solaris (1972)")) {
            media.resolve("$title/$title.mkv").also { it.parent.createDirectories() }.writeBytes(ByteArray(8))
        }
        val playable = SampleMedia.available
        if (playable) SampleMedia.video(media.resolve("Night Drive (2021)/Night.Drive.2021.720p.mkv"), seconds = 90)
        val environment = DesktopEnvironment(root.resolve("data"))
        val added = environment.addLocalFolder(media) as FolderAddition.Added
        runBlocking { if (playable) environment.pipeline.refresh() else environment.library.scan(added.source) }

        try {
            Shell(environment, reader).use { shell ->
                val app = shell.app
                fun step(ask: String, done: () -> Boolean) {
                    say(ask)
                    shell.await(ask, timeoutMs = 60_000, condition = done)
                }
                shell.awaitShown("Recently Added")
                shell.ui { shell.window.requestFocus() }
                assertEquals(InputModality.POINTER, shell.ui { app.input.modality })

                step("press the D-pad down, or push the left stick down") { app.input.modality == InputModality.DIRECTIONAL }
                assertEquals(family, shell.ui { app.input.controllerFamily })
                shell.awaitShown(confirm) // the navigation bar now names this controller's buttons
                assertEquals(Destination.Home, shell.ui { app.navigator.current })

                step("move to any poster and press $confirm") { app.navigator.current is Destination.Details }
                step("press $cancel to go back") { app.navigator.current == Destination.Home }
                step("press ${name(GamepadButton.START)} to open the media sources") { app.navigator.current == Destination.Sources }
                step("press $cancel to go back") { app.navigator.current == Destination.Home }
                step("press ${name(GamepadButton.SELECT)} for fullscreen") { app.platform.fullscreen }
                step("press ${name(GamepadButton.SELECT)} again to leave fullscreen") { !app.platform.fullscreen }

                if (playable) {
                    val movie = environment.library.movies().first { it.item.title == "Night Drive" }.item.id
                    shell.ui { app.navigator.open(Destination.Details(movie)) }
                    shell.awaitShown("Version")
                    step("press the D-pad down to reach Play, then $confirm") { app.playback?.state?.value?.status == PlayerStatus.PLAYING }
                    val session = assertNotNull(shell.ui { app.playback })
                    step("press $confirm to pause") { session.state.value.status == PlayerStatus.PAUSED }
                    val paused = session.state.value.positionMs
                    step("press the D-pad right to skip forward") { session.state.value.positionMs >= paused + 9_000 }
                    step("press ${name(GamepadButton.START)} for the player's menus") { session.menu != null }
                    step("press $cancel to close the menu") { session.menu == null && app.playback != null }
                    step("press $cancel to leave the player") { app.playback == null }
                }
                say("done: this $family controller drives Reflux")
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
