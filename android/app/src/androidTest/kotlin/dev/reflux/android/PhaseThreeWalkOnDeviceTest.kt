package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.library.createProfile
import dev.reflux.library.deleteProfile
import dev.reflux.library.profiles
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.KeyboardMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Device walks of Phase 3, through the application itself and judged by its accessibility tree, with a screenshot of
 * each step kept in the app's files (`walks/`):
 *
 * - **F-P3:** Quick View from a card, by a long touch (handheld) or a remote's Menu (television), and Back closing it;
 * - **B-017:** Switch profile and Log out in Settings once there are two profiles, Log out asking who is watching;
 * - **F-P4:** on a television, the remote types on the controller's grid (its action column), the presentation never the
 *   desk keyboard.
 *
 * It plays in the application's own library, so it belongs to a scratch (debug) build.
 */
@RunWith(AndroidJUnit4::class)
class PhaseThreeWalkOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val environment get() = (context.applicationContext as RefluxApplication).environment
    private lateinit var directory: File
    private lateinit var folder: DocumentTreeSource
    private var activity: Activity? = null
    private lateinit var app: AppState
    private val television get() = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    private val shots get() = File(if (television) context.filesDir else context.getExternalFilesDir(null), "walks").also { it.mkdirs() }
    private val model = android.os.Build.MODEL.replace(' ', '_')

    @Before
    fun setUp() {
        directory = File(context.filesDir, "walk3-${System.nanoTime()}").also { it.mkdirs() }
        File(directory, "Jungle Walk (2016)/Jungle.Walk.2016.mp4").also { it.parentFile.mkdirs() }
            .outputStream().use { out -> instrumentation.context.assets.open("red.mp4").use { it.copyTo(out) } }
        folder = DocumentTreeSource(FolderTree(directory), "test://walk3", "Walk three")
        environment.library.addSource(folder, "test://walk3")
        environment.registry.register(folder)
        runBlocking { environment.pipeline.refresh(folder.descriptor.id) }
    }

    @After
    fun tearDown() {
        runCatching { instrumentation.runOnMainSync { activity?.finish() } }
        runCatching { environment.library.removeSource(folder.descriptor.id) }
        directory.deleteRecursively()
    }

    private fun start() {
        val started = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        activity = started
        app = MainActivity::class.java.getDeclaredField("app").apply { isAccessible = true }.get(started) as AppState
        if (!television) instrumentation.runOnMainSync { app.onSignal(dev.reflux.core.input.InputSignal.Touch) }
        unlockIfAsked()
    }

    /** As in Phase 2's walk: a PIN given for the run (`-e unlockPin`) only, never written anywhere. */
    private fun unlockIfAsked() {
        val pin = InstrumentationRegistry.getArguments().getString("unlockPin") ?: return
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            val gate = app.profileGate
            if (gate is dev.reflux.ui.state.ProfileGate.Unlock && gate.profile.active) {
                instrumentation.runOnMainSync { app.enterPin(pin) }
                until("unlocked") { app.profileGate == null }
                return
            }
            if (gate == null && app.navigator.current == Destination.Home) return
            Thread.sleep(100)
        }
    }

    private fun until(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(50)
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

    private fun AccessibilityNodeInfo.words() = (text ?: contentDescription)?.toString().orEmpty()
    private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)
    private fun says(words: String) = nodes().any { it.isVisibleToUser && words in it.words() }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "phase3-$model-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun press(x: Float, y: Float, durationMs: Long) {
        val down = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        fun send(action: Int, at: Long) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(down, at, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, down)
        SystemClock.sleep(durationMs)
        send(MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
    }

    private fun remote(keyCode: Int) {
        instrumentation.uiAutomation.executeShellCommand("input keyevent $keyCode").close()
        Thread.sleep(700)
    }

    private val density get() = context.resources.displayMetrics.density

    private fun quickViewShown() = nodes().any { it.isVisibleToUser && it.paneTitle?.toString() == "Quick view" }

    @Test
    fun quickViewOpensOnAFilmAndBackClosesIt() {
        start()
        // Whatever the device's library shows first: a card is a tall node with a description.
        fun cards() = nodes().filter { it.isVisibleToUser && it.bounds().height() > 100 * density && it.contentDescription != null && it.bounds().width() < it.bounds().height() }
        if (television) {
            until("Home") { app.navigator.current == Destination.Home && app.profileGate == null }
            Thread.sleep(1500)
            // Home starts on the hero's action (B-062: Details where it cannot play); down into the first row, then the
            // remote's Menu on the card there.
            var tries = 0
            while (app.quickView == null && tries++ < 4) {
                remote(KeyEvent.KEYCODE_DPAD_DOWN)
                remote(KeyEvent.KEYCODE_MENU)
                if (app.navigator.current != Destination.Home) remote(KeyEvent.KEYCODE_BACK)
            }
        } else {
            until("a card on Home") { cards().isNotEmpty() }
            val card = cards().first()
            press(card.bounds().exactCenterX(), card.bounds().exactCenterY(), 900)
        }
        until("Quick View") { quickViewShown() }
        Thread.sleep(500)
        shot("quick-view")
        // Its main action, or, for a work that cannot play now (its source away), Favorite and Details only.
        assertTrue(says("Play") || says("Resume") || (says("Details") && nodes().none { it.isVisibleToUser && it.paneTitle == null && it.words() == "Play" }), "its actions")
        assertEquals(Destination.Home, app.navigator.current, "opening it opened no page")
        assertNull(app.playback, "and played nothing")
        if (television) remote(KeyEvent.KEYCODE_BACK) else instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        until("closed") { !quickViewShown() && app.quickView == null }
        assertEquals(Destination.Home, app.navigator.current, "Back closed it, and went nowhere else")
    }

    @Test
    fun settingsOffersSwitchProfileAndLogOutOnceThereAreTwo() {
        val sam = environment.library.createProfile("Walk Sam")
        try {
            start()
            instrumentation.runOnMainSync { app.go(Destination.Settings) }
            until("Settings' profile entries") { says("Switch profile") && says("Log out") }
            shot("settings-profile")
            val main = app.activeProfile
            // Logging out of a profile with a PIN would ask for it again: only without a PIN, or with the run's own PIN.
            if (main?.hasPin == true && InstrumentationRegistry.getArguments().getString("unlockPin") == null) return
            instrumentation.runOnMainSync { app.logOut() }
            until("who is watching") { says("Who's watching?") }
            shot("logged-out")
            instrumentation.runOnMainSync { app.chooseProfile(app.profiles.first { it.id == main!!.id }) }
            unlockIfAsked()
            until("back in") { app.profileGate == null && app.activeProfile?.id == main!!.id }
        } finally {
            runCatching { instrumentation.runOnMainSync { activity?.finish() } }
            runBlocking { environment.library.deleteProfile(sam, null, null) }
            assertTrue(environment.library.profiles().none { it.id == sam })
        }
    }

    @Test
    fun aRemoteTypesOnTheControllersGrid() {
        assumeTrue(television)
        start()
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the field") { app.navigator.current == Destination.Search }
        Thread.sleep(800)
        // OK on the field opens the Reflux keyboard.
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        until("the keyboard") { app.keyboard != null }
        assertEquals(KeyboardMode.CONTROLLER, app.keyboard?.mode, "a television has the grid only")
        // From q: right into the row, OK types; up to the digits, left wraps to the action column's Erase.
        remote(KeyEvent.KEYCODE_DPAD_RIGHT)
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        until("w typed") { app.searchQuery == "w" }
        remote(KeyEvent.KEYCODE_DPAD_UP)
        repeat(2) { remote(KeyEvent.KEYCODE_DPAD_LEFT) }
        assertEquals(dev.reflux.ui.state.KeyboardKey.Erase, app.keyboard?.chosen, "left from the row's start wraps to its last key")
        shot("keyboard-grid")
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        until("erased") { app.searchQuery.isEmpty() }
        remote(KeyEvent.KEYCODE_BACK)
        until("closed") { app.keyboard == null }
    }
}
