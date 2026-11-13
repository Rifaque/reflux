package dev.reflux.android

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.reflux.android.playback.Media3Player
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.AppState
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The activity's end lets go of what plays (docs/planning/M10-PLAYER-SPEC.md, F8). Before M10 an activity that was destroyed
 * without finishing (recreated for a change it did not handle, or taken by the system) left its playback running: the engine
 * stayed open and kept the audio focus and the video surface, its place was never recorded, and the next activity started
 * another beside it.
 *
 * This plays in the application's own library (it adds a folder of its own and takes it away again), so it belongs to a
 * scratch build (`-Preflux.android.applicationId=...`), never to an installation someone uses.
 */
@RunWith(AndroidJUnit4::class)
class ActivityRecreationOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun appOf(activity: Activity): AppState =
        MainActivity::class.java.getDeclaredField("app").apply { isAccessible = true }.get(activity) as AppState

    private fun resumed(): Activity? {
        var found: Activity? = null
        instrumentation.runOnMainSync { found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() }
        return found
    }

    private fun until(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    @Test
    fun anActivityThatIsRecreatedLetsGoOfThePlaybackAndRecordsItsPlace() {
        val environment = (context.applicationContext as RefluxApplication).environment
        val directory = File(context.filesDir, "recreation-${System.nanoTime()}").also { it.mkdirs() }
        val film = File(directory, "Night Drive (2024)/Night.Drive.2024.1080p.mp4").also { it.parentFile.mkdirs() }
        instrumentation.context.assets.open("clip.mp4").use { input -> film.outputStream().use(input::copyTo) }
        val tree = FolderTree(directory)
        val folder = DocumentTreeSource(tree, "test://recreation", "Recreation")
        environment.library.addSource(folder, "test://recreation")
        environment.registry.register(folder)
        try {
            runBlocking { environment.pipeline.refresh() }
            val movie = environment.library.movies().single { it.item.title == "Night Drive" }.item.id
            val opened = Media3Player.open.get()

            val first = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val app = appOf(first)
            instrumentation.runOnMainSync { app.play(movie) }
            until("the film to be playing") { app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs > 1_500 } == true }
            assertEquals(opened + 1, Media3Player.open.get(), "one engine for one playback")
            // A place is only kept past the first minute of a work (WatchRules): somewhere worth coming back to.
            instrumentation.runOnMainSync { app.playback!!.seekTo(90_000) }
            until("the film to be past its first minute") { app.playback?.state?.value?.let { it.status == PlayerStatus.PLAYING && it.positionMs in 90_000..120_000 } == true }
            val reached = app.playback!!.state.value.positionMs

            // The activity ends without finishing, as when the system recreates it for a change it does not handle.
            instrumentation.runOnMainSync { first.recreate() }
            until("the old activity to be destroyed") { first.isDestroyed }

            until("the engine to be let go") { Media3Player.open.get() == opened }
            val second = checkNotNull(resumed()) { "the activity came back" }
            assertTrue(second !== first)
            assertNull(appOf(second).playback, "the new activity starts with nothing playing: there is one owner, not two")
            until("the place to be recorded") { (environment.library.watchState(movie)?.positionMs ?: 0) >= 90_000 }
            assertTrue(environment.library.watchState(movie)!!.positionMs <= reached + 10_000, "the place is where it was left")
            assertNotNull(environment.library.watchState(movie))
            // Nothing is left playing under the new activity either.
            Thread.sleep(500)
            assertEquals(opened, Media3Player.open.get())
            instrumentation.runOnMainSync { second.finish() }
            until("the activity to finish") { second.isDestroyed }
        } finally {
            environment.library.removeSource(folder.descriptor.id)
            directory.deleteRecursively()
        }
    }
}
