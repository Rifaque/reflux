package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.ui.state.SUPPORT_URL
import dev.reflux.ui.state.Destination
import dev.reflux.library.createProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The support report as a person gets it (docs/RELEASE.md, "Diagnostics export"): from About, only when asked,
 * handed to them by the platform, and with nothing in it that names their media, their servers, or them.
 */
@OptIn(ExperimentalTestApi::class)
class SupportReportUiTest {
    @Test
    fun aboutSavesAReportThatSaysWhatHappenedAndNothingPrivate() {
        val world = populated()
        // A second viewer: the report says how many profiles there are, never their names (M8).
        world.library.createProfile("Priyanka")
        val platform = FakePlatform()
        // A problem recorded the way a careless caller might write one, with a title, a path, and an address in it.
        platform.report("Playback of \"Heat\" failed: C:\\Users\\someone\\Movies\\Heat (1995)\\Heat.1995.1080p.BluRay.x264.mkv via http://192.168.1.20:8096/Videos/1?api_key=0123456789abcdef0123456789abcdef")
        screen(world, platform) { app ->
            runOnIdle { app.navigator.open(Destination.About) }
            runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Save a support report")) }
            awaitText("Save a support report")
            onNodeWithText("Save a support report").performClick()
            await("the report to be handed over") { onAllNodesWithText("Saved in your Downloads folder as", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val (name, text) = platform.supportReportsDelivered.entries.single()
            assertTrue(Regex("""reflux-support-\d{8}-\d{6}\.txt""").matches(name), name)
            assertTrue(text.startsWith("Reflux support report\nReflux ${app.platform.build.label}\n"), text)
            assertTrue("1. Folder: available; " in text, text)
            assertTrue("Video decoders: " in text, text)
            assertTrue("Playback of \"<private>\" failed: <path>.mkv via http://<server>/…" in text, text)
            assertTrue("Profiles: 2" in text, text)
            for (title in listOf("Heat", "Blade Runner", "Breaking Bad", "Severance", "someone", "192.168.1.20", "0123456789abcdef", "Priyanka")) {
                assertFalse(text.contains(title, ignoreCase = true), "\"$title\" is in the report:\n$text")
            }
            assertEquals(0, platform.linksOpened.size, "nothing was opened or sent")
        }
    }

    @Test
    fun helpOpensTheSupportPageOrSaysWhereItIs() {
        val platform = FakePlatform()
        screen(populated(), platform) { app ->
            runOnIdle { app.navigator.open(Destination.About) }
            awaitText("Help and feedback")
            onNodeWithText("Help and feedback").performClick()
            await("the support page to open") { platform.linksOpened == listOf(SUPPORT_URL) }
            platform.canOpenLinks = false
            onNodeWithText("Help and feedback").performClick()
            awaitText("Support and feedback: reflux.rsxdevelops.dev/support")
        }
    }
}
