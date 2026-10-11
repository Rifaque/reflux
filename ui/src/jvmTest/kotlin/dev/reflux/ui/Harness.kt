package dev.reflux.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.reflux.ui.player.PlayerOverlay
import dev.reflux.ui.state.AppState
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.assertNotNull

/** Where `-Preflux.screenshots=<dir>` asks rendered screens to be saved for visual review, if anywhere. */
private val screenshots: File? = System.getProperty("reflux.screenshots")?.takeIf { it.isNotBlank() }?.let(::File)

/**
 * Renders the real experience layer offscreen over [world], the way a shell does: the browsing screens,
 * and the player overlay above a stand-in for the video surface while something plays.
 */
@OptIn(ExperimentalTestApi::class)
internal fun screen(
    world: TestWorld,
    platform: FakePlatform = FakePlatform(),
    width: Int = 1440,
    height: Int = 900,
    block: DesktopComposeUiTest.(AppState) -> Unit,
) = runDesktopComposeUiTest(width, height) {
    var app: AppState? = null
    setContent {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(world.library, world.pipeline, world.registry::resolve, platform, scope).also { app = it } }
        Box(Modifier.fillMaxSize()) {
            RefluxApp(state)
            state.playback?.let { session ->
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5A3E2B), Color(0xFF0E1A2B)))))
                PlayerOverlay(state, session)
            }
        }
    }
    waitForIdle()
    block(app!!)
}

internal fun populated(metadata: Boolean = true) = TestWorld(metadata).also { runBlocking { it.populate() } }

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.awaitText(text: String) =
    waitUntil("\"$text\" to appear", 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.await(what: String, condition: () -> Boolean) = waitUntil(what, 10_000, condition)

/** Saves what is on screen as `<name>.png` when screenshots were requested. */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.save(name: String) {
    waitForIdle()
    val directory = screenshots ?: return
    directory.mkdirs()
    val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
    File(directory, "$name.png").writeBytes(assertNotNull(png).bytes)
}
