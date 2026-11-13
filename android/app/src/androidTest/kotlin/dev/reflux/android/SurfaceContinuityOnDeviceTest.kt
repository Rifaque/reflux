package dev.reflux.android

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.playback.TrackType
import dev.reflux.core.source.PlaybackTarget
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The M10 probe P1 (docs/planning/M10-PLAYER-SPEC.md, section 3), kept as the regression test of section 5.2: one
 * `SurfaceView` moves between the whole window and a small box under a playing Media3 engine, five times, and the
 * engine does not notice. The same engine, the same view, one decoder started and never switched off, no surface
 * destroyed, the position only forward, the subtitle track still selected.
 *
 * The view is moved the way the toolkit moves it (its layout changes; its parent and visibility do not), once with the
 * default buffer and once with a fixed-size one.
 */
@RunWith(AndroidJUnit4::class)
class SurfaceContinuityOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private val players = ArrayList<Media3Player>()

    @After
    fun cleanUp() {
        players.forEach(Media3Player::close)
        worlds.forEach(DeviceWorld::close)
    }

    private class Observed(val created: Int, val destroyed: Int, val positions: List<Long>, val janky: String)

    private fun run(name: String, fixedBuffer: Boolean): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val world = DeviceWorld(name).also { worlds += it }
        val clip = world.clip("clip.mp4")
        val subtitle = File(clip.parentFile, "clip.en.srt").apply { writeText("1\n00:00:01,000 --> 00:02:30,000\nA line that lasts a long time.\n\n") }
        val player = Media3Player(context, world.environment.capabilities).also { players += it }

        // The application's own window, with the probe's frame laid over what it shows.
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as Activity
        val root = FrameLayout(activity)
        instrumentation.runOnMainSync {
            root.setBackgroundColor(android.graphics.Color.BLACK)
            activity.addContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        Thread.sleep(1_000)
        var surface: SurfaceView? = null
        var created = 0
        var destroyed = 0
        instrumentation.runOnMainSync {
            surface = SurfaceView(activity).also { view ->
                view.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { created++ }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) { destroyed++ }
                })
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                player.attach(view)
            }
        }
        val view = surface!!
        player.load(
            PlaybackRequest(
                PlaybackTarget(world.tree.open(clip.path)), "Clip",
                subtitles = listOf(SubtitleAttachment(PlaybackTarget(world.tree.open(subtitle.path)), "en")),
            ),
        )
        player.await("playback to start") { it.status == PlayerStatus.PLAYING && it.positionMs > 500 }
        val track = player.await("the subtitle track") { state -> state.subtitleTracks.any { it.type == TrackType.SUBTITLE } }.subtitleTracks.first()
        player.selectSubtitle(track.id)
        player.await("the subtitle to be selected") { it.selectedSubtitleId == track.id }
        if (fixedBuffer) instrumentation.runOnMainSync { view.holder.setFixedSize(1280, 720) }
        Thread.sleep(500)

        val startsBefore = player.decoderStarts
        val disabledBefore = player.videoDisabled
        val droppedBefore = player.framesDropped
        val createdBefore = created
        val engineState = player.state.value
        instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${context.packageName} reset").close()

        val positions = ArrayList<Long>()
        val width = root.width
        val height = root.height
        val dockWidth = (width * 0.3f).toInt()
        val dockHeight = dockWidth * 9 / 16
        val dockLeft = 24
        val dockTop = height - dockHeight - 160
        var screenshots = 0
        repeat(5) { round ->
            // Into the dock, and out of it: 300 ms each, the way the toolkit lays a moving child out frame by frame.
            for (toDock in listOf(true, false)) {
                val done = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    ValueAnimator.ofFloat(if (toDock) 0f else 1f, if (toDock) 1f else 0f).apply {
                        duration = 300
                        addUpdateListener {
                            val f = it.animatedValue as Float
                            view.layoutParams = FrameLayout.LayoutParams(
                                (width + (dockWidth - width) * f).toInt(), (height + (dockHeight - height) * f).toInt(),
                            ).also { lp -> lp.leftMargin = (dockLeft * f).toInt(); lp.topMargin = (dockTop * f).toInt() }
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) = done.countDown()
                        })
                        start()
                    }
                }
                assertTrue(done.await(5, TimeUnit.SECONDS), "the move finished")
                positions += player.state.value.positionMs
                if (toDock && round == 0) {
                    // Seen from outside, at rest in the dock: the picture is in the box and nowhere else.
                    Thread.sleep(700)
                    instrumentation.uiAutomation.takeScreenshot()?.let { shot ->
                        File(context.filesDir, "p1-$name-docked.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        screenshots++
                    }
                }
                Thread.sleep(150)
            }
        }
        Thread.sleep(500)
        val gfx = StringBuilder()
        instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${context.packageName}").use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().useLines { lines ->
                lines.filter { it.contains("Total frames rendered") || it.contains("Janky frames") || it.contains("50th percentile") || it.contains("90th percentile") || it.contains("99th percentile") }.forEach { gfx.append(it.trim()).append(" | ") }
            }
        }
        val after = player.state.value
        val report = "$name: decoderStarts +${player.decoderStarts - startsBefore}, videoDisabled +${player.videoDisabled - disabledBefore}, " +
            "framesDropped +${player.framesDropped - droppedBefore}, surfaceCreated +${created - createdBefore}, surfaceDestroyed $destroyed, " +
            "status ${after.status}, subtitle still selected ${after.selectedSubtitleId == track.id}, " +
            "positions ${positions.joinToString(",")}, same decoder ${after.videoDecoder == engineState.videoDecoder}, screenshots $screenshots, gfx: $gfx"
        println("P1 RESULT $report")
        File(context.filesDir, "p1-$name.txt").writeText(report)

        assertEquals(startsBefore, player.decoderStarts, "no decoder was started again ($report)")
        assertEquals(disabledBefore, player.videoDisabled, "video was never switched off ($report)")
        assertEquals(0, destroyed, "no surface was destroyed ($report)")
        assertEquals(createdBefore, created, "no surface was created again ($report)")
        assertEquals(track.id, after.selectedSubtitleId, "the subtitle track stayed selected ($report)")
        assertTrue(positions.zipWithNext().all { (a, b) -> b >= a }, "position only went forward ($report)")
        assertTrue(after.status == PlayerStatus.PLAYING, "still playing ($report)")
        assertSame(view, surface, "the same view")
        activity.finish()
        return report
    }

    @Test
    fun theDefaultBufferSurvivesBeingMoved() {
        run("default", fixedBuffer = false)
    }

    @Test
    fun aFixedSizeBufferSurvivesBeingMoved() {
        run("fixed", fixedBuffer = true)
    }
}
