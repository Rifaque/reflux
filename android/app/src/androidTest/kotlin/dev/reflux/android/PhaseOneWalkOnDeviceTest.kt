package dev.reflux.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.reflux.android.storage.DocumentTreeSource
import dev.reflux.core.model.MediaId
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Device walks of two Phase 1 fixes, through the application itself and judged by what is on the screen (its
 * accessibility tree, the same one a screen reader reads), with a screenshot of each step kept for the record in the
 * app's external files (`walks/`):
 *
 * - **B-033:** a favourite whose source is removed the way Sources removes it stays a favourite, its page says it is no
 *   longer in the library, and "Remove from favorites" removes it without the source.
 * - **B-005:** while the library is being updated, its sign never lies over a page's heading, a text field, or any other
 *   label, on Home, Movies, and Search, at this device's own size.
 *
 * It plays in the application's own library, so it belongs to a scratch (debug) build.
 */
@RunWith(AndroidJUnit4::class)
class PhaseOneWalkOnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val environment get() = (context.applicationContext as RefluxApplication).environment
    private lateinit var directory: File
    private lateinit var folder: DocumentTreeSource
    private var activity: Activity? = null
    private lateinit var app: AppState
    private val shots = File(context.getExternalFilesDir(null), "walks").also { it.mkdirs() }

    @Before
    fun setUp() {
        directory = File(context.filesDir, "walk-${System.nanoTime()}").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { instrumentation.runOnMainSync { activity?.finish() } }
        if (::folder.isInitialized) runCatching { environment.library.removeSource(folder.descriptor.id) }
        directory.deleteRecursively()
    }

    private fun addFolder(name: String) {
        folder = DocumentTreeSource(FolderTree(directory), "test://$name", name)
        environment.library.addSource(folder, "test://$name")
        environment.registry.register(folder)
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

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun aFavouriteOfARemovedSourceIsKeptAndCanBeRemoved() {
        File(directory, "Jungle Walk (2016)/Jungle.Walk.2016.mp4").also { it.parentFile.mkdirs() }
            .outputStream().use { out -> instrumentation.context.assets.open("red.mp4").use { it.copyTo(out) } }
        addFolder("Jungle demo")
        runBlocking { environment.pipeline.refresh(folder.descriptor.id) }
        val film: MediaId = environment.library.movies().single { it.item.title == "Jungle Walk" }.item.id
        environment.library.setFavorite(film, true)
        start()

        // The source goes the way the Sources page removes it.
        runBlocking { app.removeSource(folder.descriptor.id).join() }
        assertTrue(environment.library.favorites().any { it.item.id == film }, "the favourite is kept when its source goes")

        instrumentation.runOnMainSync { app.navigator.open(Destination.Details(film)) }
        until("the page to say it is gone") { withWords("This is no longer in your library") != null }
        val remove = assertNotNull(run { until("Remove from favorites") { withWords("Remove from favorites") != null }; withWords("Remove from favorites") })
        shot("b033-${android.os.Build.MODEL}-gone-page")

        var target: AccessibilityNodeInfo? = remove
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(assertNotNull(target, "a pressable Remove from favorites").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        until("the favourite to go") { environment.library.favorites().none { it.item.id == film } }
        Thread.sleep(600)
        shot("b033-${android.os.Build.MODEL}-removed")
    }

    @Test
    fun theLibrarysSignNeverCoversALabelOrAField() {
        // Enough files that the update is on screen for a while; each is a name with nothing playable inside.
        repeat(600) { n ->
            File(directory, "Walk Film $n (${1950 + n % 70})/Walk.Film.$n.mkv").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(16))
        }
        addFolder("Walk library")
        start()
        val labels = Regex("^(Scanning .*|Reading media details|Finding titles and details|Fetching artwork)$")
        val seen = mutableListOf<String>()
        for (destination in listOf(Destination.Home, Destination.Movies, Destination.Search)) {
            instrumentation.runOnMainSync { app.navigator.open(destination) }
            // New files for each page, so that each update has work to do while the page is on screen.
            val page = destination::class.simpleName
            repeat(400) { n -> File(directory, "$page Film $n (${1950 + n % 70})/$page.Film.$n.mkv").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(16)) }
            Thread.sleep(800)
            val update = app.refresh(folder.descriptor.id)
            var checked = false
            val end = System.currentTimeMillis() + 60_000
            while (update.isActive && System.currentTimeMillis() < end) {
                val all = nodes().filter { it.isVisibleToUser }
                val sign = all.firstOrNull { labels.matches(it.words()) }
                if (sign != null) {
                    val area = sign.bounds()
                    val others = all.filter { it !== sign && it.childCount == 0 && (it.words().isNotEmpty() || it.isEditable) && !labels.matches(it.words()) }
                    val covered = others.filter { Rect.intersects(area, it.bounds()) }
                    if (!checked) shot("b005-${android.os.Build.MODEL}-${destination::class.simpleName}")
                    assertTrue(covered.isEmpty(), "${destination::class.simpleName}: the sign '${sign.words()}' at $area covers ${covered.map { it.words().ifEmpty { "a field" } to it.bounds() }}")
                    checked = true
                }
                Thread.sleep(100)
            }
            runBlocking { update.join() }
            seen += "${destination::class.simpleName}: ${if (checked) "sign seen and clear" else "no sign shown"}"
        }
        println("B-005 walk on ${android.os.Build.MODEL}: $seen")
        assertTrue(seen.any { it.endsWith("sign seen and clear") }, "the sign was seen at least once: $seen")
    }
}
