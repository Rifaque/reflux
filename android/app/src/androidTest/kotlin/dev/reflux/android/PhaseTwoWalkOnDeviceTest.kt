package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import dev.reflux.library.createProfile
import dev.reflux.library.deleteProfile
import dev.reflux.library.profiles
import dev.reflux.library.setPin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Device walks of Phase 2's touch work, through the application itself and judged by its accessibility tree, with a
 * screenshot of each step kept in the app's external files (`walks/`):
 *
 * - **B-036:** Search with the system keyboard: the bar steps aside while it is up and comes back when it is put away,
 *   even with the field still focused; the next Back goes back; the query is kept.
 * - **B-034, B-035, B-037:** a short placeholder, no "Try", chips with touch targets of at least 48 dp, a designed
 *   empty state.
 * - **B-046:** no Profile button on Profiles; there is one on Home.
 * - **B-048:** a pull at the top of Home finds a film added since.
 *
 * It plays in the application's own library, so it belongs to a scratch (debug) build.
 */
@RunWith(AndroidJUnit4::class)
class PhaseTwoWalkOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val environment get() = (context.applicationContext as RefluxApplication).environment
    private lateinit var directory: File
    private lateinit var folder: DocumentTreeSource
    private var activity: Activity? = null
    private lateinit var app: AppState
    private val shots = File(context.getExternalFilesDir(null), "walks").also { it.mkdirs() }
    private val model = android.os.Build.MODEL.replace(' ', '_')

    @Before
    fun setUp() {
        directory = File(context.filesDir, "walk2-${System.nanoTime()}").also { it.mkdirs() }
        File(directory, "Jungle Walk (2016)/Jungle.Walk.2016.mp4").also { it.parentFile.mkdirs() }
            .outputStream().use { out -> instrumentation.context.assets.open("red.mp4").use { it.copyTo(out) } }
        folder = DocumentTreeSource(FolderTree(directory), "test://walk2", "Walk two")
        environment.library.addSource(folder, "test://walk2")
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
        instrumentation.runOnMainSync { app.onSignal(dev.reflux.core.input.InputSignal.Touch) }
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
    private fun withWords(words: String) = nodes().firstOrNull { it.words() == words && it.isVisibleToUser }
    private fun says(words: String) = nodes().any { it.isVisibleToUser && words in it.words() }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Whether the system keyboard is up, as the shell itself reads it from the window's insets (B-036). */
    private fun imeShown(): Boolean = app.systemKeyboardShown

    private fun tap(node: AccessibilityNodeInfo) {
        val r = node.bounds()
        touch(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), 60)
    }

    private fun touch(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long, steps: Int = 2) {
        val down = SystemClock.uptimeMillis()
        // A finger, as a touchscreen reports one (a tool type of its own, which is what makes the gesture a touch).
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        fun send(action: Int, x: Float, y: Float, at: Long) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(down, at, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, x0, y0, down)
        for (i in 1..steps) {
            val f = i / steps.toFloat()
            SystemClock.sleep(durationMs / steps)
            send(MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, SystemClock.uptimeMillis())
        }
        send(MotionEvent.ACTION_UP, x1, y1, SystemClock.uptimeMillis())
    }

    private fun back() {
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(700)
    }

    private val density get() = context.resources.displayMetrics.density

    @Test
    fun searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving() {
        start()
        until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the field") { nodes().any { it.isEditable } }
        val phone = context.resources.configuration.smallestScreenWidthDp < 600
        // B-034: a short placeholder, whole.
        assertTrue(says("Search your library"), "the short placeholder")
        // B-035: no "Try"; chips with a finger's target.
        assertFalse(nodes().any { it.isVisibleToUser && it.words() == "Try" }, "no Try label")
        val chip = assertNotNull(withWords("unfinished shows")?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n })
        assertTrue(chip.bounds().height() >= (48 * density).toInt() - 1, "a chip is at least 48 dp tall: ${chip.bounds().height() / density} dp")
        shot("p2-search-$model")

        // B-036: the system keyboard up; on a phone the bar steps aside while it is.
        tap(nodes().first { it.isEditable })
        until("the keyboard", 8_000) { imeShown() }
        Thread.sleep(500)
        shot("p2-search-keyboard-$model")
        if (phone) assertFalse(withWords("Home") != null, "the bar steps aside for the keyboard")
        // A system keyboard commits text; it is not a hardware keyboard's key presses (which would make Reflux treat the
        // viewer as a keyboard user, for whom the first Back lets go of the field). The query is committed as the IME would.
        instrumentation.runOnMainSync { app.searchQuery = "zzqx" }
        until("the empty state") { says("No matches for") }
        // B-037: a designed empty state with a next action.
        assertTrue(says("Clear search") && says("Browse movies"))
        // The system's Back puts the keyboard away; the bar comes back though the field still has focus.
        back()
        until("the keyboard gone", 8_000) { !imeShown() }
        Thread.sleep(500)
        shot("p2-search-keyboard-gone-$model")
        if (phone) assertNotNull(withWords("Home"), "the bar is back once the keyboard is put away")
        assertEquals(Destination.Search, app.navigator.current, "putting the keyboard away is not leaving")
        // The next Back goes back.
        back()
        until("home") { app.navigator.current == Destination.Home }
        // The query is kept.
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the query") { app.searchQuery == "zzqx" && says("No matches for") }
    }

    @Test
    fun profilesHasNoButtonToItselfAndHomeHasOne() {
        start()
        until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
        val phone = context.resources.configuration.smallestScreenWidthDp < 600 &&
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val profileButton = { nodes().any { it.isVisibleToUser && (it.words().startsWith("Profile and settings") || it.words().startsWith("Profile, ")) } }
        if (phone) assertTrue(profileButton(), "Home has the profile entry")
        instrumentation.runOnMainSync { app.go(Destination.Profiles) }
        until("Profiles") { says("Add a profile") }
        Thread.sleep(400)
        shot("p2-profiles-$model")
        if (phone) assertFalse(profileButton(), "Profiles has no button leading to itself (B-046)")
        instrumentation.runOnMainSync { app.go(Destination.Settings) }
        until("Settings") { says("Clear watch history") }
        if (phone) assertTrue(profileButton(), "from Settings the profile entry leads back to Profiles")
    }

    @Test
    fun aPullAtTheTopOfHomeFindsWhatWasAdded() {
        start()
        until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
        File(directory, "Paterson Walk (2016)/Paterson.Walk.2016.mkv").also { it.parentFile.mkdirs() }
            .outputStream().use { out -> instrumentation.context.assets.open("blue.mkv").use { it.copyTo(out) } }
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val from = metrics.heightPixels * 0.3f
        touch(x, from, x, from + 260 * density, 500, steps = 20)
        until("the pull to start a refresh", 5_000) { app.pullRefreshing || environment.library.movies().any { it.item.title == "Paterson Walk" } }
        Thread.sleep(300)
        shot("p2-pulling-$model")
        until("the refresh to find it", 180_000) { environment.library.movies().any { it.item.title == "Paterson Walk" } }
        until("the sign gone") { !app.pullRefreshing }
        Thread.sleep(600)
        shot("p2-pulled-$model")
    }

    @Test
    fun forgotPinOffersTheDeviceLockAndCancellingChangesNothing() {
        // Forgot PIN (DECISIONS.md, 2026-11-19): on a handheld with a screen lock, "Use device lock" opens the system's
        // own prompt; cancelled, nothing changes. A scratch profile, deleted afterwards with its PIN.
        val key = environment.pinKey
        val sam = environment.library.createProfile("Walk Sam")
        try {
            environment.library.setPin(sam, "2580", key)
            start()
            until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
            until("the profiles") { app.profiles.any { it.id == sam } }
            instrumentation.runOnMainSync { app.chooseProfile(app.profiles.first { it.id == sam }) }
            until("the PIN") { says("Enter this profile's PIN.") }
            instrumentation.runOnMainSync { app.forgotPin() }
            until("the choices") { says("Forgot Walk Sam's PIN?") }
            Thread.sleep(400)
            shot("p2-forgot-pin-$model")
            if (!says("Use device lock")) {
                println("Forgot PIN walk on $model: no device lock offered (no screen lock set, or Android < 10)")
                return
            }
            instrumentation.runOnMainSync { app.removeForgottenPinWithSystem() }
            // The system's prompt, drawn by the system, over Reflux.
            until("the system's prompt", 8_000) { instrumentation.uiAutomation.windows.any { w -> w.root?.packageName?.toString()?.let { it != context.packageName } == true } || nodes().any { it.packageName?.toString() != context.packageName } }
            Thread.sleep(800)
            shot("p2-forgot-pin-system-prompt-$model")
            back()
            until("nothing changed") { says("Nothing was changed.") }
            kotlin.test.assertTrue(environment.library.profiles().first { it.id == sam }.hasPin, "the PIN is still there")
            shot("p2-forgot-pin-cancelled-$model")
        } finally {
            runCatching { instrumentation.runOnMainSync { app.forgotPin(false) } }
            runCatching { environment.library.deleteProfile(sam, "2580", key) }
        }
    }
}
