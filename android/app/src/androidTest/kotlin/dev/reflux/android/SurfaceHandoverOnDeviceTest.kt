package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.PlayerPresentation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-001: one film after another, through the application itself, judged by what is on the screen. A red film and a blue one
 * (generated, `red.mp4` and `blue.mkv`); after each change the picture read off the video surface must be the new film's
 * colour while the new film's sound and clock run: never the last film's frame with the next one's sound.
 *
 * It covers what reaches a new engine: from the mini-player (the old engine let go and the new one made in one go, the path
 * that left the picture behind), after closing, after a film played to its end, after stopping, and several changes in a row.
 * Like [PlayerAccessibilityOnDeviceTest] it plays in the application's own library, so it belongs to a scratch build.
 */
@RunWith(AndroidJUnit4::class)
class SurfaceHandoverOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val environment get() = (context.applicationContext as RefluxApplication).environment
    private lateinit var directory: File
    private lateinit var folder: DocumentTreeSource
    private lateinit var activity: Activity
    private lateinit var app: AppState
    private var red = MediaId("")
    private var blue = MediaId("")
    private val pixels = HandlerThread("pixels").apply { start() }

    @Before
    fun setUp() {
        directory = File(context.filesDir, "handover-${System.nanoTime()}").also { it.mkdirs() }
        for ((asset, path) in listOf("red.mp4" to "Red Film (2021)/Red.Film.2021.mp4", "blue.mkv" to "Blue Film (2022)/Blue.Film.2022.mkv")) {
            val file = File(directory, path).also { it.parentFile.mkdirs() }
            instrumentation.context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
        }
        folder = DocumentTreeSource(FolderTree(directory), "test://handover", "Handover")
        environment.library.addSource(folder, "test://handover")
        environment.registry.register(folder)
        runBlocking { environment.pipeline.refresh() }
        red = environment.library.movies().single { it.item.title == "Red Film" }.item.id
        blue = environment.library.movies().single { it.item.title == "Blue Film" }.item.id
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        app = MainActivity::class.java.getDeclaredField("app").apply { isAccessible = true }.get(activity) as AppState
        instrumentation.runOnMainSync { app.onSignal(dev.reflux.core.input.InputSignal.Touch) }
    }

    @After
    fun tearDown() {
        runCatching {
            instrumentation.runOnMainSync { app.playback?.close() }
            until("the engine to be let go") { app.playback == null }
            instrumentation.runOnMainSync { activity.finish() }
        }
        environment.library.removeSource(folder.descriptor.id)
        directory.deleteRecursively()
        pixels.quitSafely()
    }

    private fun until(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(50)
        }
    }

    private enum class Colour { RED, BLUE, OTHER }

    /** Every video surface in the window. There is one while something plays. */
    private fun surfaces(): List<SurfaceView> {
        val found = ArrayList<SurfaceView>()
        fun walk(view: View) {
            if (view is SurfaceView) found += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        instrumentation.runOnMainSync { walk(activity.window.decorView) }
        return found
    }

    /** The colour at the middle of what the video surface shows now, or null when it has nothing to read. */
    private fun shown(): Colour? {
        val surface = surfaces().singleOrNull() ?: return null
        if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) return null
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(surface, bitmap, { result = it; done.countDown() }, Handler(pixels.looper))
        if (!done.await(5, TimeUnit.SECONDS) || result != PixelCopy.SUCCESS) return null
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val (r, b) = (pixel shr 16 and 0xff) to (pixel and 0xff)
        return when {
            r > 150 && b < 100 -> Colour.RED
            b > 150 && r < 100 -> Colour.BLUE
            else -> Colour.OTHER
        }
    }

    private fun colourOf(item: MediaId) = if (item == red) Colour.RED else Colour.BLUE

    /**
     * Waits for [item] to be what plays, and its picture to be what shows. While its sound and clock run, the screen must
     * never go on showing the other film: that is the defect.
     */
    private fun playing(item: MediaId, step: String) {
        until("$step: ${colourOf(item)} to play") {
            val session = app.playback
            session?.entry?.item?.id == item && session.state.value.status == PlayerStatus.PLAYING && session.state.value.positionMs > 300
        }
        val expected = colourOf(item)
        val other = if (expected == Colour.RED) Colour.BLUE else Colour.RED
        val seen = ArrayList<Colour?>()
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            val colour = shown()
            seen += colour
            if (colour == expected) break
            Thread.sleep(150)
        }
        assertEquals(1, surfaces().size, "$step: one video surface")
        assertEquals(expected, seen.last(), "$step: the picture is the film that plays, seen $seen")
        // The other film's frame is allowed only before this one's first frame has been drawn, which it now has.
        assertTrue(seen.dropWhile { it != expected }.none { it == other }, "$step: no frame of the last film after this one's, seen $seen")
        assertEquals(item, app.playback?.entry?.item?.id, "$step: the title and clock are this film's")
    }

    @Test
    fun fromTheMiniPlayerToAnotherFilm() {
        org.junit.Assume.assumeFalse("a television has no mini-player", app.platform.television)
        instrumentation.runOnMainSync { app.play(red) }
        playing(red, "red")
        instrumentation.runOnMainSync { app.playback!!.collapse() }
        until("the mini-player") { app.playback?.presentation == PlayerPresentation.MINI }
        // Play on another film while one is in the dock: the old engine goes and the new one comes without a pause between.
        instrumentation.runOnMainSync { app.play(blue) }
        playing(blue, "blue from the dock")
    }

    @Test
    fun closeThenPlayAnotherAtOnce() {
        instrumentation.runOnMainSync { app.play(red) }
        playing(red, "red")
        instrumentation.runOnMainSync {
            app.playback!!.close()
            app.play(blue)
        }
        playing(blue, "blue right after closing red")
        instrumentation.runOnMainSync { app.playback!!.close() }
        until("closed") { app.playback == null }
        // Closed, and opened again: the same film from where it was, on a surface of its own.
        instrumentation.runOnMainSync { app.play(blue) }
        playing(blue, "blue reopened")
    }

    @Test
    fun aFilmPlayedToItsEndThenAnother() {
        instrumentation.runOnMainSync { app.play(red) }
        playing(red, "red")
        val duration = app.playback!!.state.value.durationMs!!
        instrumentation.runOnMainSync { app.playback!!.seekTo(duration - 1_500) }
        until("red to end", 15_000) { app.playback?.state?.value?.status.let { it == PlayerStatus.ENDED || it == PlayerStatus.IDLE } || app.playback == null }
        instrumentation.runOnMainSync { app.playback?.close() }
        until("closed") { app.playback == null }
        instrumentation.runOnMainSync { app.play(blue) }
        playing(blue, "blue after red ended")
    }

    @Test
    fun stopThenANewPlayback() {
        instrumentation.runOnMainSync { app.play(blue) }
        playing(blue, "blue")
        instrumentation.runOnMainSync { app.playback!!.handle(dev.reflux.core.input.SemanticAction.STOP) }
        until("stopped") { app.playback == null }
        instrumentation.runOnMainSync { app.play(red) }
        playing(red, "red after stopping blue")
    }

    @Test
    fun severalChangesInARow() {
        var item = red
        repeat(5) { round ->
            instrumentation.runOnMainSync {
                app.playback?.close()
                app.play(item)
            }
            playing(item, "change ${round + 1}")
            item = if (item == red) blue else red
        }
    }
}
