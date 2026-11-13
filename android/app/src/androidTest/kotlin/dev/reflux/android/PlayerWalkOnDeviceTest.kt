package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.Media3Player
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.ui.state.PlayerMenu
import dev.reflux.ui.state.AppState
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a check: the harness of the device walks (docs/planning/M10-PLAYER-SPEC.md, section 13). It puts a film in the
 * application's own library, opens the application, plays it, and then keeps the application up while a person (or adb)
 * drives it with real gestures, logging what the session says under the tag `M10Walk`. Commands arrive as lines in
 * `files/walk.cmd` of the application (`play`, `collapse`, `expand`, `close`, `stop`), so a walk can put the player where
 * it wants it without touching the screen. It runs only when asked (`-e walk true`), because it never finishes by itself,
 * and only in a scratch build: it adds a folder to the library it runs in and leaves it there.
 */
@RunWith(AndroidJUnit4::class)
class PlayerWalkOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun appOf(activity: Activity): AppState =
        MainActivity::class.java.getDeclaredField("app").apply { isAccessible = true }.get(activity) as AppState

    private fun current(): Activity? {
        var found: Activity? = null
        instrumentation.runOnMainSync {
            found = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                ?: androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STOPPED).firstOrNull()
        }
        return found
    }

    @Test
    fun keepThePlayerUpForAWalk() {
        assumeTrue("a walk, not a check", InstrumentationRegistry.getArguments().getString("walk") == "true")
        val environment = (context.applicationContext as RefluxApplication).environment
        val directory = File(context.filesDir, "walk-media").also { it.mkdirs() }
        val films = listOf("Night Drive (2024)" to "clip.mp4", "Chapters (2024)" to "chapters.mkv")
        for ((folder, asset) in films) {
            val file = File(directory, "$folder/${folder.substringBefore(" (")}.2024.1080p.${asset.substringAfterLast('.')}").also { it.parentFile.mkdirs() }
            if (!file.exists()) instrumentation.context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
        }
        // Subtitle files beside the film: English and French, so the subtitle list has something to choose from.
        val night = File(directory, "Night Drive (2024)")
        File(night, "Night.Drive.2024.1080p.en.srt").writeText("1\n00:00:01,000 --> 00:02:50,000\nAn English line that lasts a long time.\n\n")
        File(night, "Night.Drive.2024.1080p.fr.srt").writeText("1\n00:00:01,000 --> 00:02:50,000\nUne ligne en francais.\n\n")
        val tree = FolderTree(directory)
        val folder = DocumentTreeSource(tree, "test://walk", "Walk")
        if (environment.library.sources().none { it.id == folder.descriptor.id }) environment.library.addSource(folder, "test://walk")
        // `-e http true`: what is scanned is the folder, what is played is served over HTTP from the host (adb reverse tcp:8099),
        // which can be made slow, stalled, or taken away: a real wait and a real failure.
        val http = InstrumentationRegistry.getArguments().getString("http") == "true"
        if (http) environment.registry.register(HttpBacked(folder, "http://127.0.0.1:8099/media")) else environment.registry.register(folder)
        runBlocking { environment.pipeline.refresh() }
        val movie = environment.library.movies().first { it.item.title == "Night Drive" }.item.id

        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val command = File(context.filesDir, "walk.cmd").also { it.delete() }
        Log.i(TAG, "ready; engines open: ${Media3Player.open.get()}")
        var shown: String? = null
        val end = System.currentTimeMillis() + 20 * 60_000
        var attached: Activity = activity
        while (System.currentTimeMillis() < end) {
            val now = current() ?: attached
            attached = now
            val app = appOf(now)
            if (command.exists()) {
                val lines = command.readLines().map(String::trim).filter(String::isNotEmpty)
                command.delete()
                for (line in lines) {
                    Log.i(TAG, "command: $line")
                    when (line) {
                        "stop" -> return
                        "play" -> instrumentation.runOnMainSync { app.play(movie) }
                        "collapse" -> instrumentation.runOnMainSync { app.playback?.collapse() }
                        "expand" -> instrumentation.runOnMainSync { app.playback?.expand() }
                        "close" -> instrumentation.runOnMainSync { app.playback?.close() }
                        "back" -> instrumentation.runOnMainSync { app.dispatch(SemanticAction.BACK) }
                        "pause" -> instrumentation.runOnMainSync { app.playback?.handle(SemanticAction.PLAY_PAUSE) }
                        "retry" -> instrumentation.runOnMainSync { app.playback?.retry() }
                        "touch" -> instrumentation.runOnMainSync { app.playback?.touch() }
                        "next" -> instrumentation.runOnMainSync { app.playback?.playNext() }
                        else -> when {
                            line.startsWith("seek:") -> instrumentation.runOnMainSync { app.playback?.seekTo(line.removePrefix("seek:").toLong() * 1000) }
                            line.startsWith("menu:") -> instrumentation.runOnMainSync { app.playback?.toggleMenu(PlayerMenu.valueOf(line.removePrefix("menu:"))) }
                            line.startsWith("choose:") -> line.removePrefix("choose:").split(":").let { (m, i) -> instrumentation.runOnMainSync { app.playback?.choose(PlayerMenu.valueOf(m), i.toInt()) } }
                        }
                    }
                }
            }
            val session = app.playback
            val line = if (session == null) "none; route=${app.backRoute}; engines=${Media3Player.open.get()}" else {
                val state = session.state.value
                "${session.presentation} ${state.status} pos=${state.positionMs / 1000}s route=${app.backRoute} frame=${app.videoFrame::class.simpleName} " +
                    "engines=${Media3Player.open.get()} error=${session.error?.message} chapters=${state.chapters.size} sub=${state.selectedSubtitleId} activity=${now.hashCode()}"
            }
            if (line.substringBefore(" pos=") != shown?.substringBefore(" pos=") || System.currentTimeMillis() % 5_000 < 600) {
                Log.i(TAG, line)
                shown = line
            }
            Thread.sleep(500)
        }
    }

    private companion object {
        const val TAG = "M10Walk"
    }
}

/** A folder source whose files are played from an address instead of from storage: the same library, a stream that can misbehave. */
private class HttpBacked(private val inner: FileEnumeratingSource, private val base: String) : FileEnumeratingSource by inner {
    override suspend fun playbackTarget(path: String): PlaybackTarget =
        PlaybackTarget(base + "/" + path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") })
}
