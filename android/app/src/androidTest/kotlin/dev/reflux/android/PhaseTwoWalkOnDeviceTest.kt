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
    // A television's shared storage is out of adb's reach (Fire OS): its shots go where `run-as` can fetch them.
    private val shots get() = File(if (television) context.filesDir else context.getExternalFilesDir(null), "walks").also { it.mkdirs() }
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
        // A handheld's walks are a finger's; a television's are its remote's, which the keys themselves say.
        if (!television) instrumentation.runOnMainSync { app.onSignal(dev.reflux.core.input.InputSignal.Touch) }
        unlockIfAsked()
    }

    /**
     * A device whose debug build's profile has a PIN asks for it at start. The owner may hand that PIN to a run as an
     * argument (`am instrument -e unlockPin …`); it is never written into the test or the repository. Without it, a walk
     * on such a device stops at the question, as it should.
     */
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
        // A handheld's walk (touch, the system keyboard, the device lock): a television has none of them.
        org.junit.Assume.assumeFalse(television)
        start()
        until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the field") { nodes().any { it.isEditable } }
        val phone = context.resources.configuration.smallestScreenWidthDp < 600
        // B-034: a short placeholder, whole.
        shot("p2-search-placeholder-$model")
        assertTrue(says("Search your library"), "the short placeholder: ${nodes().filter { it.isEditable }.map { it.words() }}")
        // B-035: no "Try"; chips with a finger's target.
        assertFalse(nodes().any { it.isVisibleToUser && it.words() == "Try" }, "no Try label")
        // B-057: the idle page's quick finds, each a finger's target, offered because this library has something for them.
        until("the index") { says("Start with") }
        val chip = assertNotNull(withWords("Not seen yet")?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n })
        assertTrue(chip.bounds().height() >= (48 * density).toInt() - 1, "a quick find is at least 48 dp tall: ${chip.bounds().height() / density} dp")
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
        // A handheld's walk (touch, the system keyboard, the device lock): a television has none of them.
        org.junit.Assume.assumeFalse(television)
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
        // A handheld's walk (touch, the system keyboard, the device lock): a television has none of them.
        org.junit.Assume.assumeFalse(television)
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

    @Test
    fun interfaceHapticsAreOffUntilTurnedOnInSettings() {
        // F-001: off by default (an acceptance criterion); Settings offers the switch where the device can vibrate; on, it is
        // kept. The walk turns it back to what it was. What it feels like is for a hand to say (VERIFICATION.md).
        start()
        until("Home") { app.navigator.current == Destination.Home }
        val before = app.haptics.interfaceOn
        println("Interface haptics on $model before the walk: ${if (before) "on" else "off"}")
        try {
            instrumentation.runOnMainSync { app.go(Destination.Settings) }
            until("Settings") { app.navigator.current == Destination.Settings }
            Thread.sleep(800)
            if (!app.haptics.interfaceAvailable) {
                // A device without a vibrator (the Pad 7 has none): the switch is not offered, and nothing can turn it on.
                println("Interface haptics on $model: no vibrator, so no switch")
                shot("p2-haptics-none-$model")
                kotlin.test.assertFalse(says("Interface haptics"), "no switch where nothing can vibrate")
                return
            }
            // The switch is further down the page: a finger's swipe up until it shows.
            val screen = context.resources.displayMetrics
            repeat(8) {
                if (says("Interface haptics")) return@repeat
                touch(screen.widthPixels * 0.6f, screen.heightPixels * 0.75f, screen.widthPixels * 0.6f, screen.heightPixels * 0.35f, 300, steps = 10)
                Thread.sleep(500)
            }
            until("the switch", 5_000) { says("Interface haptics") }
            Thread.sleep(300)
            shot("p2-haptics-setting-$model")
            instrumentation.runOnMainSync { app.setInterfaceHaptics(true) }
            until("on") { app.haptics.interfaceOn }
            Thread.sleep(500)
            shot("p2-haptics-on-$model")
            until("the choice kept") { environment.library.setting(dev.reflux.ui.state.Haptics.INTERFACE_HAPTICS) == "on" }
        } finally {
            instrumentation.runOnMainSync { app.setInterfaceHaptics(before) }
        }
    }

    private val television get() = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)

    /**
     * A remote's key, injected by the system's input service as adb's `input keyevent` does: a device event through the
     * window manager to the focused window, like a remote's. (The instrumentation's own key sending was seen to differ.)
     */
    private fun remote(keyCode: Int) {
        instrumentation.uiAutomation.executeShellCommand("input keyevent $keyCode").close()
        Thread.sleep(700)
    }

    private fun focusedWords(): String? = nodes().firstOrNull { it.isFocused }?.let { node ->
        node.words().ifEmpty { (0 until node.childCount).mapNotNull { node.getChild(it)?.words() }.firstOrNull { it.isNotEmpty() }.orEmpty() }
    }

    @Test
    fun onATelevisionOkOnACardOpensItsPageOnPlayAndOkAgainPlays() {
        // B-053 with a remote (a television only).
        org.junit.Assume.assumeTrue(television)
        start()
        // Home; the film is in its rows, which since Phase 3 may start below a featured work in progress and Continue Watching.
        until("Home") { app.navigator.current == Destination.Home }
        // Down from the hero to the row's first card, then OK.
        remote(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(8) { if (app.navigator.current == Destination.Home && focusedWords()?.contains("Jungle Walk") != true) remote(KeyEvent.KEYCODE_DPAD_DOWN) }
        shot("p2-tv-card-$model")
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        until("details") { app.navigator.current is Destination.Details }
        Thread.sleep(1200)
        shot("p2-tv-details-arrived-$model")
        try {
            // The focused node is Play: by its words, or (where the system leaves a merged button's words on its text alone, as
            // Fire OS does) by being the box Play's words are in.
            until("Play focused", 8_000) {
                val focused = nodes().firstOrNull { it.isFocused } ?: return@until false
                val play = nodes().firstOrNull { it.isVisibleToUser && (it.words() == "Play" || it.words().startsWith("Resume")) }
                focusedWords()?.let { it.startsWith("Play") || it.startsWith("Resume") } == true ||
                    (play != null && focused.bounds().contains(play.bounds().centerX(), play.bounds().centerY()))
            }
            Thread.sleep(1500)
            shot("p2-tv-details-play-$model")
        } catch (e: AssertionError) {
            val node = nodes().firstOrNull { it.isFocused }
            throw AssertionError("Play not focused: focus on '${focusedWords()}' (${node?.className} at ${node?.bounds()}), modality ${app.input.modality}", e)
        }
        kotlin.test.assertNull(app.playback, "nothing plays on arrival")
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        until("playing") { app.playback != null }
        Thread.sleep(1500)
        shot("p2-tv-playing-$model")
        remote(KeyEvent.KEYCODE_BACK)
        until("left the player") { app.playback == null || app.watching == null }
        runCatching { instrumentation.runOnMainSync { app.playback?.close() } }
        until("details again") { app.navigator.current is Destination.Details }
        remote(KeyEvent.KEYCODE_BACK)
        until("home") { app.navigator.current == Destination.Home }
    }

    @Test
    fun onATelevisionTheRemoteTypesInSearchWithTheRefluxKeyboard() {
        // B-004, B-023, B-054 with a remote: OK opens the Reflux keyboard, OK types, Back puts it away with the text kept.
        org.junit.Assume.assumeTrue(television)
        start()
        until("Home") { app.navigator.current == Destination.Home }
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the field") { app.navigator.current == Destination.Search && app.textInputActive }
        // The field reports itself before it is ready for OK on a slower television (the Google TV emulator).
        Thread.sleep(800)
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        try {
            until("the Reflux keyboard", 6_000) { app.keyboard != null }
        } catch (e: AssertionError) {
            throw AssertionError("no keyboard: focus on '${focusedWords()}', typing ${app.textInputActive}, modality ${app.input.modality}", e)
        }
        // The system keyboard is kept away (the field asks for it as it takes focus, and Reflux hides it within frames).
        until("the system keyboard kept away", 4_000) { !app.systemKeyboardShown }
        val session = app.keyboard!!
        for (letter in "jun") {
            val (row, column) = session.rows.withIndex().firstNotNullOf { (r, keys) -> keys.indexOf(dev.reflux.ui.state.KeyboardKey.Type(letter.toString())).takeIf { it >= 0 }?.let { r to it } }
            while (session.row != row) remote(if (session.row < row) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
            while (session.column != column) remote(if (session.column < column) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            remote(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        until("typed") { app.searchQuery == "jun" }
        until("found") { says("Jungle Walk") }
        shot("p2-tv-keyboard-$model")
        remote(KeyEvent.KEYCODE_BACK)
        until("put away") { app.keyboard == null }
        kotlin.test.assertEquals("jun", app.searchQuery, "the query is kept")
        kotlin.test.assertEquals(Destination.Search, app.navigator.current)
        shot("p2-tv-keyboard-gone-$model")
    }

    /**
     * A walk by hand: the test film in the library, Reflux open, and then the owner's controller for `manualMinutes`
     * (an instrumentation argument; without it, nothing). What the device says of the controllers is printed.
     */
    @Test
    fun aWalkByHandWithAController() {
        val minutes = InstrumentationRegistry.getArguments().getString("manualMinutes")?.toIntOrNull() ?: return
        start()
        until("Home") { app.navigator.current == Destination.Home }
        val input = context.getSystemService(android.hardware.input.InputManager::class.java)
        for (id in input.inputDeviceIds) {
            val device = input.getInputDevice(id) ?: continue
            if (device.sources and android.view.InputDevice.SOURCE_GAMEPAD != android.view.InputDevice.SOURCE_GAMEPAD) continue
            val motors = if (android.os.Build.VERSION.SDK_INT >= 31) device.vibratorManager.vibratorIds.size else if (@Suppress("DEPRECATION") device.vibrator.hasVibrator()) 1 else 0
            println("Controller on $model: ${device.name}, motors: $motors")
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        var shotAt = 0L
        while (System.currentTimeMillis() < end) {
            if (System.currentTimeMillis() > shotAt) {
                shot("p2-byhand-${(end - System.currentTimeMillis()) / 1000}-$model")
                shotAt = System.currentTimeMillis() + 20_000
            }
            Thread.sleep(500)
        }
        println("Rumble available for the last controller used on $model: ${runCatching { app.platform.controllerRumble?.available() }.getOrNull()}")
    }

    /** A controller's button, sent by the system's input service as a gamepad's (`input gamepad keyevent`), as a paired pad's arrive. */
    private fun pad(keyCode: Int) {
        instrumentation.uiAutomation.executeShellCommand("input gamepad keyevent $keyCode").close()
        Thread.sleep(700)
        // The accessibility tree catches up with a move after the move: a check read before it would press once too often.
        runCatching { instrumentation.uiAutomation.waitForIdle(300, 3_000) }
    }

    /** Whether what has focus is (or holds) the visible words [words]: by its words, or by its box holding theirs. */
    private fun focusIsOn(words: String): Boolean {
        val focused = nodes().firstOrNull { it.isFocused } ?: return false
        if (focusedWords()?.contains(words) == true) return true
        val target = nodes().filter { it.isVisibleToUser && it.words().contains(words) }
        return target.any { focused.bounds().contains(it.bounds().centerX(), it.bounds().centerY()) }
    }

    private fun prompts(): String = nodes().firstNotNullOfOrNull { node -> node.viewIdResourceName?.takeIf { it.startsWith("prompts:") } }.orEmpty()

    @Test
    fun onATelevisionAControllerBrowsesChoosesTypesAndHidesItsHints() {
        // Phase 2 with a controller on a television (B-039, F-P2, B-053, B-023/B-054, B-055): gamepad buttons from the system.
        org.junit.Assume.assumeTrue(television)
        start()
        // Home; the film is in its rows, which since Phase 3 may start below a featured work in progress and Continue Watching.
        until("Home") { app.navigator.current == Destination.Home }
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        until("a controller in hand") { app.input.controllerFamily != null && app.input.modality == dev.reflux.core.input.InputModality.DIRECTIONAL }
        // Down to the film's card (found by where it is: Fire OS leaves a focused card's node without words), A: its page, on
        // Play; nothing plays; B back.
        repeat(8) { if (!focusIsOn("Jungle Walk")) pad(KeyEvent.KEYCODE_DPAD_DOWN) }
        assertTrue(focusIsOn("Jungle Walk"), "on the film's card")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        until("details") { app.navigator.current is Destination.Details }
        until("Play focused", 8_000) {
            val focused = nodes().firstOrNull { it.isFocused } ?: return@until false
            val play = nodes().firstOrNull { it.isVisibleToUser && it.words() == "Play" }
            play != null && focused.bounds().contains(play.bounds().centerX(), play.bounds().centerY())
        }
        kotlin.test.assertNull(app.playback)
        shot("p2-pad-details-$model")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("home") { app.navigator.current == Destination.Home }
        // X on the focused film is its Quick View since Phase 3 (F-P3; this walk said "X does nothing" before it): B closes it
        // and the page is unchanged. Then Y to the sidebar and back.
        val before = app.navigator.current
        pad(KeyEvent.KEYCODE_BUTTON_X)
        until("Quick View by X") { app.quickView != null }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("Quick View closed by B") { app.quickView == null }
        kotlin.test.assertEquals(before, app.navigator.current, "the page is unchanged")
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        Thread.sleep(500)
        shot("p2-pad-sidebar-$model")
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        Thread.sleep(500)
        // The bumpers between destinations.
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        until("Movies by RB") { app.navigator.current == Destination.Movies }
        pad(KeyEvent.KEYCODE_BUTTON_L1)
        until("Home by LB") { app.navigator.current == Destination.Home }
        // LT hides the hints and shows them again.
        pad(KeyEvent.KEYCODE_BUTTON_L2)
        until("hints hidden") { app.hintsHidden }
        shot("p2-pad-hints-hidden-$model")
        pad(KeyEvent.KEYCODE_BUTTON_L2)
        until("hints shown") { !app.hintsHidden }
        // Search: A on the field opens the Reflux keyboard; A types, X erases, B closes it with the text kept.
        instrumentation.runOnMainSync { app.go(Destination.Search) }
        until("the field") { app.navigator.current == Destination.Search && app.textInputActive }
        // The field takes focus as Search settles (as PhaseThreeWalkOnDeviceTest waits for too).
        Thread.sleep(800)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        until("the Reflux keyboard") { app.keyboard != null }
        val session = app.keyboard!!
        for (letter in "jux") {
            val (row, column) = session.rows.withIndex().firstNotNullOf { (r, keys) -> keys.indexOf(dev.reflux.ui.state.KeyboardKey.Type(letter.toString())).takeIf { it >= 0 }?.let { r to it } }
            while (session.row != row) pad(if (session.row < row) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
            while (session.column != column) pad(if (session.column < column) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            pad(KeyEvent.KEYCODE_BUTTON_A)
        }
        until("typed") { app.searchQuery == "jux" }
        pad(KeyEvent.KEYCODE_BUTTON_X)
        until("erased") { app.searchQuery == "ju" }
        shot("p2-pad-keyboard-$model")
        until("found") { says("Jungle Walk") }
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("closed") { app.keyboard == null }
        kotlin.test.assertEquals("ju", app.searchQuery)
        val input = context.getSystemService(android.hardware.input.InputManager::class.java)
        for (id in input.inputDeviceIds) {
            val device = input.getInputDevice(id) ?: continue
            if (device.sources and android.view.InputDevice.SOURCE_GAMEPAD != android.view.InputDevice.SOURCE_GAMEPAD) continue
            @Suppress("DEPRECATION") println("Controller on $model: ${device.name}, vibrator: ${device.vibrator.hasVibrator()}")
        }
    }

    @Test
    fun onATelevisionAControllerWalksEveryPageWhateverTheLibrary() {
        // Phase 2's close (2026-11-20), a controller on a television, written for any library (a television's own library
        // may be too large for the test film to be on screen): Movies, Details on Play, focus restored after Back, Shows,
        // Search typed with the Reflux keyboard, the shortcuts, Start's Sources, and a confirmation cancelled by B.
        org.junit.Assume.assumeTrue(television)
        start()
        until("Home") { app.navigator.current == Destination.Home && nodes().any { it.isFocused } }
        pad(KeyEvent.KEYCODE_DPAD_DOWN)
        until("a controller in hand") { app.input.controllerFamily != null && app.input.modality == dev.reflux.core.input.InputModality.DIRECTIONAL }
        shot("p2-tvc-home-$model")
        // Movies by RB; into the grid, along to the walk's own film (a television's other films may be on a source that is
        // not connected, whose page rightly puts focus past a Play that cannot play: B-053).
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        until("Movies") { app.navigator.current == Destination.Movies }
        pad(KeyEvent.KEYCODE_DPAD_RIGHT)
        until("a film focused", 6_000) { nodes().any { it.isFocused } }
        repeat(10) { if (!focusIsOn("Jungle Walk")) pad(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertTrue(focusIsOn("Jungle Walk"), "on the walk's film: focus on '${focusedFilm()}'")
        val film: String? = "Jungle Walk"
        shot("p2-tvc-movies-$model")
        // Its page: Play has focus and nothing plays; B comes back to the same film.
        pad(KeyEvent.KEYCODE_BUTTON_A)
        until("details") { app.navigator.current is Destination.Details }
        until("Play focused", 8_000) {
            val focused = nodes().firstOrNull { it.isFocused } ?: return@until false
            val play = nodes().firstOrNull { it.isVisibleToUser && (it.words() == "Play" || it.words().startsWith("Resume")) }
            play != null && focused.bounds().contains(play.bounds().centerX(), play.bounds().centerY())
        }
        kotlin.test.assertNull(app.playback, "nothing plays on arrival")
        shot("p2-tvc-details-$model")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("Movies again") { app.navigator.current == Destination.Movies }
        if (film != null) until("focus back on $film", 6_000) { focusIsOn(film) }
        // Shows, then Search, by RB.
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        until("Shows") { app.navigator.current == Destination.Shows }
        until("focus on Shows", 6_000) { nodes().any { it.isFocused } }
        shot("p2-tvc-shows-$model")
        pad(KeyEvent.KEYCODE_BUTTON_R1)
        until("Search") { app.navigator.current == Destination.Search && app.textInputActive }
        Thread.sleep(800)
        pad(KeyEvent.KEYCODE_BUTTON_A)
        until("the Reflux keyboard", 6_000) { app.keyboard != null }
        until("the system keyboard kept away", 4_000) { !app.systemKeyboardShown }
        val word = (film ?: "a").lowercase().filter { it in 'a'..'z' }.take(3).ifEmpty { "a" }
        val session = app.keyboard!!
        for (letter in word + "x") {
            val (row, column) = session.rows.withIndex().firstNotNullOf { (r, keys) -> keys.indexOf(dev.reflux.ui.state.KeyboardKey.Type(letter.toString())).takeIf { it >= 0 }?.let { r to it } }
            while (session.row != row) pad(if (session.row < row) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
            while (session.column != column) pad(if (session.column < column) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            pad(KeyEvent.KEYCODE_BUTTON_A)
        }
        until("typed") { app.searchQuery == word + "x" }
        pad(KeyEvent.KEYCODE_BUTTON_X)
        until("erased") { app.searchQuery == word }
        if (film != null) until("found $film") { says(film) }
        shot("p2-tvc-keyboard-$model")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("put away") { app.keyboard == null }
        kotlin.test.assertEquals(word, app.searchQuery, "the query is kept")
        kotlin.test.assertEquals(Destination.Search, app.navigator.current)
        // X has no browsing meaning (reserved); Y opens and closes the sidebar; LT hides and shows the hints.
        val before = app.navigator.current
        pad(KeyEvent.KEYCODE_BUTTON_X)
        kotlin.test.assertEquals(before, app.navigator.current, "X does nothing while browsing")
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        pad(KeyEvent.KEYCODE_BUTTON_Y)
        pad(KeyEvent.KEYCODE_BUTTON_L2)
        until("hints hidden") { app.hintsHidden }
        pad(KeyEvent.KEYCODE_BUTTON_L2)
        until("hints shown") { !app.hintsHidden }
        // Start opens Sources; B comes back with focus kept on the page.
        pad(KeyEvent.KEYCODE_BUTTON_START)
        until("Sources") { app.navigator.current == Destination.Sources }
        until("focus in Sources", 6_000) { nodes().any { it.isFocused } }
        shot("p2-pad-sources-$model")
        // On a television the first Back from a top-level page goes to the navigation, the next one leaves (NAVIGATION.md, 2).
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("focus in the navigation", 6_000) { nodes().any { it.isFocused } }
        kotlin.test.assertEquals(Destination.Sources, app.navigator.current, "the first B goes to the navigation, not away")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        until("back from Sources") { app.navigator.current != Destination.Sources }
        until("focus after Sources", 6_000) { nodes().any { it.isFocused } }
        // Nothing a pointer would do happened on the way: the controller kept the walk.
        kotlin.test.assertEquals(dev.reflux.core.input.InputModality.DIRECTIONAL, app.input.modality)
        // A confirmation's focus (A on an option, focus on Keep, B back to the option) is not walked here: on the emulator the
        // accessibility tree runs behind a move, so a check of where focus is can press once too often, and a press on the
        // wrong option of Settings changes a setting. It is PanelFocusTest's, on the television harness, and was walked by hand.
        instrumentation.runOnMainSync { app.searchQuery = "" }
    }

    /** The title of the focused film's card: the words of a title inside its box, or its own words. */
    private fun focusedFilm(): String? {
        val focused = nodes().firstOrNull { it.isFocused } ?: return null
        val box = focused.bounds()
        val inside = nodes().filter { it != focused && it.isVisibleToUser && it.words().isNotBlank() && box.contains(it.bounds()) }
        return inside.map { it.words() }.firstOrNull { words -> words.length > 2 && words.any { it.isLetter() } }
            ?: focusedWords()?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
    }

    /** A scope tab, by its words, in the top third of the screen (the bar at the bottom has the same words). */
    private fun tab(label: String): AccessibilityNodeInfo? {
        val top = context.resources.displayMetrics.heightPixels / 3
        val words = nodes().firstOrNull { it.isVisibleToUser && it.words() == label && it.bounds().top < top } ?: return null
        var n: AccessibilityNodeInfo? = words
        while (n != null && !n.isClickable) n = n.parent
        return n
    }

    @Test
    fun searchOnAPhoneFindsNarrowsEditsAndKeepsItsQuery() {
        // Phase 2's close (2026-11-20): a handheld's Search by touch, after B-057's redesign.
        org.junit.Assume.assumeFalse(television)
        start()
        until("Home") { app.navigator.current == Destination.Home && (says("Recently Added") || says("Continue Watching")) }
        instrumentation.runOnMainSync {
            app.searchQuery = ""
            app.searchScope = dev.reflux.ui.state.SearchScope.ALL
            app.go(Destination.Search)
        }
        until("the index") { says("Start with") }
        Thread.sleep(800)
        // A suggestion asks its question: the query is filled and the film is found.
        tap(assertNotNull(withWords("Not seen yet")?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n }))
        until("asked") { app.searchQuery.isNotBlank() }
        until("found") { says("Jungle Walk") && says("result") }
        shot("p2-search-results-$model")
        // Shows: nothing there, said so, with everything one tap away.
        tap(assertNotNull(tab("Shows"), "the Shows tab"))
        until("shows") { app.searchScope == dev.reflux.ui.state.SearchScope.SHOWS }
        until("nothing among shows") { says("No matches for") && !says("Jungle Walk") }
        shot("p2-search-shows-empty-$model")
        tap(assertNotNull(withWords("Search everything")?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n }))
        until("everything") { app.searchScope == dev.reflux.ui.state.SearchScope.ALL && says("Jungle Walk") }
        tap(assertNotNull(tab("Movies"), "the Movies tab"))
        until("movies") { app.searchScope == dev.reflux.ui.state.SearchScope.MOVIES && says("Jungle Walk") }
        // Editing: the field again, the system keyboard up, the query as it was; then what is typed changes the results.
        tap(nodes().first { it.isEditable })
        until("the keyboard", 8_000) { imeShown() }
        val asked = app.searchQuery
        assertTrue(nodes().any { it.isEditable && it.text?.toString() == asked }, "the query is in the field: $asked")
        instrumentation.runOnMainSync { app.searchQuery = "jung" }
        until("still found") { says("Jungle Walk") }
        back()
        until("the keyboard gone", 8_000) { !imeShown() }
        // A result opens its page; Back comes to Search with the query and the scope kept.
        val card = assertNotNull(
            nodes().firstOrNull { it.isVisibleToUser && it.words().contains("Jungle Walk") && it.bounds().top > context.resources.displayMetrics.heightPixels / 6 }
                ?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n ?: it },
            "the film's card",
        )
        tap(card)
        until("details") { app.navigator.current is Destination.Details }
        back()
        until("search again") { app.navigator.current == Destination.Search }
        assertEquals("jung", app.searchQuery, "the query is kept")
        assertEquals(dev.reflux.ui.state.SearchScope.MOVIES, app.searchScope, "the scope is kept")
        // Clear: the idle page again.
        tap(assertNotNull(withWords("Clear search")?.let { var n: AccessibilityNodeInfo? = it; while (n != null && !n.isClickable) n = n.parent; n ?: it }, "the clear button"))
        until("cleared") { app.searchQuery.isEmpty() && says("Start with") }
        shot("p2-search-cleared-$model")
        instrumentation.runOnMainSync { app.searchScope = dev.reflux.ui.state.SearchScope.ALL }
    }
}
