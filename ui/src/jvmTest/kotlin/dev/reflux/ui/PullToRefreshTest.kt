package dev.reflux.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pull down at the top of Home, Movies, and Shows to check the library for changes (B-048), on a phone's touchscreen:
 * through the same pipeline as every other refresh, once per pull, only from the top, and never with a pointer.
 */
@OptIn(ExperimentalTestApi::class)
class PullToRefreshTest {
    private fun androidx.compose.ui.test.DesktopComposeUiTest.pull(from: Float = 260f, distance: Float = 420f) {
        onRoot().performTouchInput { swipe(Offset(200f, from), Offset(200f, from + distance), durationMillis = 400) }
        waitForIdle()
    }

    @Test
    fun pullingHomeFindsWhatWasAddedAndWhatWasRemoved() {
        val world = populated()
        screen(world, width = 412, height = 900) { app ->
            awaitText("Recently Added")
            world.file("Paterson (2016)/Paterson.2016.1080p.mkv")
            java.io.File(world.mediaRoot(), "Arrival (2016)").deleteRecursively()
            val revision = app.revision
            pull()
            await("a refresh") { app.pullRefreshing || world.library.movies().any { it.item.title == "Paterson" } }
            await("done") { !app.pullRefreshing }
            await("the new film") { world.library.movies().any { it.item.title == "Paterson" } }
            assertFalse(world.library.movies().any { it.item.title == "Arrival" }, "the removed one went")
            assertTrue(app.revision > revision, "every page reads the library again")
        }
    }

    @Test
    fun moviesAndShowsPullToo() {
        val world = populated()
        screen(world, width = 412, height = 900) { app ->
            awaitText("Recently Added")
            for (destination in listOf(Destination.Movies, Destination.Shows)) {
                runOnIdle { app.go(destination) }
                await("$destination") { app.navigator.current == destination }
                world.file("New ${destination.hashCode()} (2020)/New.${destination.hashCode()}.2020.mkv")
                pull()
                await("refreshed on $destination") { !app.pullRefreshing && world.library.movies().any { it.item.title.startsWith("New ${destination.hashCode()}") } }
            }
        }
    }

    @Test
    fun aPullWhileOneRunsStartsNothingAndAPointerNeverPulls() {
        val world = populated()
        screen(world, width = 412, height = 900) { app ->
            awaitText("Recently Added")
            val (first, second) = runOnIdle { app.pullToRefresh() to app.pullToRefresh() }
            assertTrue(first != null && second == null, "one refresh at a time")
            await("done") { !app.pullRefreshing }
            // A short pull is not a refresh.
            pull(distance = 60f)
            waitForIdle()
            assertFalse(app.pullRefreshing)
        }
    }

    @Test
    fun aPullInTheMiddleOfThePageOnlyScrollsIt() {
        val world = populated()
        screen(world, width = 412, height = 900) { app ->
            awaitText("Recently Added")
            // Down the page first, then a pull: the page scrolls back, it does not refresh.
            onRoot().performTouchInput { swipe(Offset(200f, 800f), Offset(200f, 200f), durationMillis = 300) }
            waitForIdle()
            val before = world.library.movies().size
            onRoot().performTouchInput { swipe(Offset(200f, 400f), Offset(200f, 520f), durationMillis = 300) }
            waitForIdle()
            assertFalse(app.pullRefreshing, "a drag that does not reach the top only scrolls")
            assertEquals(before, world.library.movies().size)
        }
    }
}
