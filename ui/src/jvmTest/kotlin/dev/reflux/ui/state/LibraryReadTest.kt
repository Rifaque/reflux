package dev.reflux.ui.state

import dev.reflux.ui.FakePlatform
import dev.reflux.ui.TestWorld
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A library read cannot be interrupted, so it can fail after the page that asked for it has gone (a window closed while
 * its pages were reading). That failure is no one's: it must end as the page's cancellation, not reach the window as an
 * unhandled error (on desktop, the toolkit's error dialog). Seen in the desktop window tests, where a closed window's
 * last reads failed once its library had been deleted.
 */
class LibraryReadTest {
    @Test
    fun aReadThatFailsAfterItsPageHasGoneIsNotReported() = runBlocking {
        val world = TestWorld()
        val unhandled = CopyOnWriteArrayList<Throwable>()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { background ->
            val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, error -> unhandled += error })
            val app = AppState(world.library, world.pipeline, world.registry::resolve, FakePlatform(), scope, background)

            val reading = CompletableDeferred<Unit>()
            val failNow = CountDownLatch(1)
            // A page's read, still in the library when the page goes away; then the library fails it.
            val page = scope.launch {
                app.read<Unit> {
                    reading.complete(Unit)
                    failNow.await()
                    throw IllegalStateException("unable to open database file")
                }
            }
            reading.await()
            page.cancel()
            failNow.countDown()
            page.join()
            assertTrue(page.isCancelled, "the page's work ended as cancelled")
            assertTrue(unhandled.isEmpty(), "a failure no one waits for is not reported: $unhandled")

            // A page that is still there is told.
            val failed = runCatching { app.read<Unit> { throw IllegalStateException("unable to open database file") } }
            assertEquals("unable to open database file", failed.exceptionOrNull()?.message)
            scope.cancel()
        }
    }
}
