package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real titles are not all short. What identifies a work or an episode can be read in full where it is
 * shown for that purpose, at every size, and nothing the player says touches the edges of a phone.
 */
@OptIn(ExperimentalTestApi::class)
class LongTextTest {
    /** A real film's title, 180 characters long. */
    private val longest = "Night of the Day of the Dawn of the Son of the Bride of the Return of the Revenge of the Terror of the Attack of the Evil, Mutant, Alien, Flesh Eating, Hellbound, Zombified Living Dead Part 2"

    private fun world() = TestWorld(metadata = false).also { world ->
        world.file("$longest (2011).mkv")
        for (episode in 1..3) world.file("Friends/Season 1/Friends.S01E0$episode.The.One.Where.Ross.Finds.Out.About.Rachel.mkv")
        runBlocking { world.pipeline.refresh() }
    }

    private fun DesktopComposeUiTest.layoutOf(text: String, substring: Boolean = false): TextLayoutResult {
        await("\"$text\"") { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        // The heading or the row, not a placeholder that names the work inside its picture (which is cut short by design).
        val node = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().maxBy { it.boundsInRoot.width }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    @Test
    fun theLongestTitleIsReadInFullOnItsDetails() {
        for ((width, height) in listOf(1440 to 900, 412 to 915)) screen(world(), FakePlatform(artwork = false), width, height) { app ->
            app.navigator.open(Destination.Details(app.library.movies().single().item.id))
            await("the title to settle") { onAllNodesWithText("Version").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(layoutOf(longest).hasVisualOverflow, "the whole title at ${width}x$height")
        }
    }

    @Test
    fun anEpisodesNameIsReadOnAPhone() = screen(world(), FakePlatform(artwork = false), 412, 915) { app ->
        app.navigator.open(Destination.Details(app.library.shows().single().item.id))
        assertFalse(layoutOf("S1 · E1 · The One Where Ross Finds Out About Rachel").hasVisualOverflow, "not just its number")
    }

    @Test
    fun aFailureInThePlayerKeepsOffTheEdgesOfAPhone() {
        val platform = FakePlatform(artwork = false)
        screen(world(), platform, 412, 915) { app ->
            app.play(app.library.shows().single().item.id)
            await("the engine") { platform.players.isNotEmpty() && app.playback != null }
            platform.players.single().fail("Failed to open the stream: connection timed out after 30000 milliseconds", PlaybackFailure.UNKNOWN)
            await("the failure") { onAllNodesWithText("Back to library").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            val button = onAllNodesWithText("Back to library").fetchSemanticsNodes().first().boundsInRoot
            // The card's own padding is 20 dp inside a margin from the edge: the button starts well clear of it.
            assertTrue(button.left >= 30f, "starts ${button.left} dp from the edge")
        }
    }
}
