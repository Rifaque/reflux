package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.reflux.android.playback.Media3Player
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.PlayerPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What Android's accessibility framework is given by the player and the mini-player on a real device: the nodes a screen reader
 * walks, not what a screen reader says (that needs TalkBack and a person or its speech output; it has not been driven in
 * M10). The mini-player is one named pane with the picture (the way back), play or pause, and stop, each a target of at least
 * 48 dp; the full player's controls are named, clickable, and as large; nothing of one is in the tree of the other.
 *
 * Like [ActivityRecreationOnDeviceTest] it plays in the application's own library, so it belongs to a scratch build.
 */
@RunWith(AndroidJUnit4::class)
class PlayerAccessibilityOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun appOf(activity: Activity): AppState =
        MainActivity::class.java.getDeclaredField("app").apply { isAccessible = true }.get(activity) as AppState

    private fun until(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            out += node
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return out
    }

    private fun inside(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) + (0 until node.childCount).flatMap { inside(node.getChild(it)) }

    private fun described(description: String): List<AccessibilityNodeInfo> = nodes().filter { it.contentDescription?.toString() == description }

    /** Whether activating this node does something, as a screen reader would find: the node itself, or the one that holds its action. */
    private fun AccessibilityNodeInfo.acts(): Boolean {
        fun has(n: AccessibilityNodeInfo?) = n != null && (n.isClickable || n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        return has(this) || has(parent) || (0 until childCount).any { has(getChild(it)) }
    }

    private fun AccessibilityNodeInfo.size(): Pair<Int, Int> = android.graphics.Rect().also(::getBoundsInScreen).let { it.width() to it.height() }

    @Test
    fun theMiniPlayerIsOnePaneWithThreeLargeStopsAndTheFullPlayersControlsAreNamedAndLarge() {
        val environment = (context.applicationContext as RefluxApplication).environment
        val directory = File(context.filesDir, "a11y-${System.nanoTime()}").also { it.mkdirs() }
        val film = File(directory, "Night Drive (2024)/Night.Drive.2024.1080p.mp4").also { it.parentFile.mkdirs() }
        instrumentation.context.assets.open("clip.mp4").use { input -> film.outputStream().use(input::copyTo) }
        val folder = DocumentTreeSource(FolderTree(directory), "test://a11y", "A11y")
        environment.library.addSource(folder, "test://a11y")
        environment.registry.register(folder)
        try {
            runBlocking { environment.pipeline.refresh() }
            val movie = environment.library.movies().single { it.item.title == "Night Drive" }.item.id
            val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val app = appOf(activity)
            val density = context.resources.displayMetrics.density
            val minimum = (48 * density).toInt() - 2
            // A finger, as on any handheld in hand (and a screen reader is touch): the targets are the touch size.
            instrumentation.runOnMainSync { app.onSignal(dev.reflux.core.input.InputSignal.Touch) }
            instrumentation.runOnMainSync { app.play(movie) }
            until("the film to play") { app.playback?.state?.value?.status == PlayerStatus.PLAYING }

            // The full player: reveal its controls, and read them.
            instrumentation.runOnMainSync { app.playback!!.touch() }
            until("the controls") { described("Minimize player").isNotEmpty() }
            for (name in listOf("Minimize player", "Stop playing", "Pause", "Back 10 seconds", "Forward 10 seconds")) {
                val node = described(name).singleOrNull() ?: throw AssertionError("$name is one node in the player, found ${described(name).size}")
                assertTrue(node.acts(), "$name acts: $node")
                val (w, h) = node.size()
                assertTrue(w >= minimum && h >= minimum, "$name is a target of at least 48 dp: ${w}x$h px at $density")
            }
            assertTrue(described("Timeline").isNotEmpty(), "the timeline is there to be set")
            assertTrue(nodes().none { it.paneTitle?.toString() == "Mini player" }, "nothing of the dock while the player has the screen")

            // The mini-player: one pane, three stops.
            instrumentation.runOnMainSync { app.playback!!.collapse() }
            until("the mini-player") { app.playback?.presentation == PlayerPresentation.MINI && nodes().any { it.paneTitle?.toString() == "Mini player" } }
            until("the dock's controls") { described("Stop playing").isNotEmpty() }
            val dock = nodes().filter { it.paneTitle?.toString() == "Mini player" }
            assertEquals(1, dock.size, "one named part of the screen")
            for (name in listOf("Stop playing", "Pause")) {
                val node = described(name).singleOrNull() ?: throw AssertionError("$name is one node in the dock, found ${described(name).size}")
                assertTrue(node.acts(), "$name acts: $node")
                val (w, h) = node.size()
                assertTrue(w >= minimum && h >= minimum, "$name is a target of at least 48 dp: ${w}x$h px")
            }
            // Inside the dock's own pane (the page behind has a card of the same name).
            val within = inside(dock.single())
            val picture = within.firstOrNull { it.contentDescription?.toString()?.startsWith("Night Drive") == true && it.acts() }
            val box = android.graphics.Rect().also { dock.single().getBoundsInScreen(it) }
            val around = nodes().filter { n -> android.graphics.Rect().also(n::getBoundsInScreen).let { r -> android.graphics.Rect.intersects(r, box) } }
            assertNotNull(
                picture,
                "the dock has one stop that says what is playing and goes back to the player (the title and status: Android's tree has no node for the picture); dock $box holds: " + within.map { n -> "${n.className.toString().substringAfterLast('.')}[${android.graphics.Rect().also(n::getBoundsInScreen).toShortString()}]:${n.contentDescription}:${n.stateDescription}:${n.isClickable}" } +
                    " | on top of the same area: " + around.map { n -> "${n.className.toString().substringAfterLast('.')}:${n.contentDescription}:${n.paneTitle}:${n.isClickable}:${n.isImportantForAccessibility}" },
            )
            assertTrue(described("Minimize player").isEmpty() && described("Timeline").isEmpty(), "the full player's controls are not in the tree of the dock")

            // And back, through the node's own action, as a screen reader's double tap does.
            // From here, not the main thread: the action is carried out there by the framework.
            val target = if (picture.isClickable) picture else generateSequence(picture.parent) { it.parent }.firstOrNull { it.isClickable } ?: picture
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK), "the framework carried the action out")
            until("the player back") { app.playback?.presentation == PlayerPresentation.FULL }
            assertEquals(1, Media3Player.open.get().coerceAtLeast(1), "one engine throughout")
            instrumentation.runOnMainSync { app.playback?.close() }
            until("the engine to be let go") { app.playback == null }
            instrumentation.runOnMainSync { activity.finish() }
        } finally {
            environment.library.removeSource(folder.descriptor.id)
            directory.deleteRecursively()
        }
    }
}
