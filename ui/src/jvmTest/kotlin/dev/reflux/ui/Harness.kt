package dev.reflux.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
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
    density: Float = 1f,
    /** The system's text size, as Android hands it to the toolkit. */
    fontScale: Float = 1f,
    /** Around the whole application, for what a shell would provide: the system keyboard, for one. */
    around: @Composable (@Composable () -> Unit) -> Unit = { it() },
    block: DesktopComposeUiTest.(AppState) -> Unit,
) = runDesktopComposeUiTest((width * density).toInt(), (height * density).toInt()) {
    var app: AppState? = null
    setContent {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(world.library, world.pipeline, world.registry::resolve, platform, scope).also { app = it } }
        // [width] and [height] are the canvas the layout sees; a denser screen only draws it with more pixels.
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            around {
                Box(Modifier.fillMaxSize()) {
                    RefluxApp(state)
                    state.playback?.let { session ->
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5A3E2B), Color(0xFF0E1A2B)))))
                        PlayerOverlay(state, session)
                    }
                }
            }
        }
    }
    waitForIdle()
    block(app!!)
}

internal fun populated(metadata: Boolean = true) = TestWorld(metadata).also { runBlocking { it.populate() } }

/**
 * How long a screen may take to get somewhere. Generous on purpose: these tests render every frame in
 * software, and a loaded CI machine is many times slower than a desktop. Nothing waits this long when it works.
 */
private const val PATIENCE_MS = 30_000L

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.awaitText(text: String) =
    waitUntil("\"$text\" to appear", PATIENCE_MS) { saysAnywhere(text) }

/** Whether [text] is on a node, or in the description of one (a card says its title and caption as one stop). */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.saysAnywhere(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() || onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty()

/** Whether button prompts are on screen that say [text]; they are for the eye, and are not in the tree as words. */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.prompted(text: String): Boolean =
    onAllNodes(SemanticsMatcher("prompts") { it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> tag.startsWith("prompts:") && text in tag } == true }).fetchSemanticsNodes().isNotEmpty()

/** Waits for [condition]; [state] describes what was seen instead, for the failure message. */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.await(what: String, state: () -> Any? = { null }, condition: () -> Boolean) {
    try {
        waitUntil(what, PATIENCE_MS, condition)
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for $what" + (state()?.let { ". Instead: $it" } ?: ""), e)
    }
}

/** Saves what is on screen as `<name>.png` when screenshots were requested. */
@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.save(name: String) {
    waitForIdle()
    val directory = screenshots ?: return
    directory.mkdirs()
    val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
    File(directory, "$name.png").writeBytes(assertNotNull(png).bytes)
}
