package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Home while a large library is first read (a cold start on a phone took about a second, M7's release walk): the
 * Reflux loader, never a blank page, and then the library. A quick read shows no loader at all.
 */
@OptIn(ExperimentalTestApi::class)
class HomeLoadingUiTest {
    /** Every library read waits until [ms] after the first one began, as a large library's first reads do. */
    private class SlowFirstRead(private val ms: Long) : CoroutineDispatcher() {
        private val start = AtomicLong(0)
        override fun dispatch(context: CoroutineContext, block: Runnable) =
            Dispatchers.Default.dispatch(context, Runnable {
                start.compareAndSet(0, System.currentTimeMillis())
                val wait = start.get() + ms - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                block.run()
            })
    }

    @Test
    fun aSlowLibraryShowsTheLoaderAndThenHome() = screen(populated(), background = SlowFirstRead(1_500)) {
        waitUntil("the loader", 5_000) { saysAnywhere("Loading your library") }
        assertFalse(saysAnywhere("Recently Added"), "nothing of the library yet")
        awaitText("Recently Added")
        assertFalse(saysAnywhere("Loading your library"), "the loader leaves once Home is there")
    }

    @Test
    fun aQuickLibraryShowsNoLoader() = screen(populated()) {
        awaitText("Recently Added")
        assertTrue(!saysAnywhere("Loading your library"))
    }
}
