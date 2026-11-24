package dev.reflux.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.reflux.playback.mpv.MpvGlRender
import dev.reflux.playback.mpv.MpvPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * The Linux desktop's video, drawn by Compose as part of the player's scene (B-084, the owner's brief of 2026-11-24): libmpv's
 * render API renders each frame into a texture in **Skiko's own OpenGL context**, on the toolkit's event thread at the moment
 * Skia draws the window, and Skia draws that texture like any image, with the player's controls, scrims and subtitles composed
 * over it, in the one window.
 *
 * **One context.** Nothing is shared between contexts or GPUs and nothing crosses threads: libmpv renders through a framebuffer
 * on the texture just before Skia's own drawing of the frame, and the frame never leaves the GPU (no read-back; decoding itself
 * may copy, see DesktopPlatform.renderedHwdec). After libmpv's OpenGL calls Skia is told its cached state is stale
 * (`resetGLAll`). A separate context for libmpv in Skiko's share group was built and measured (2026-11-24): it changed nothing
 * in memory or correctness, so the simpler design stays (CACHY-011).
 *
 * **Ownership.** Skiko owns the context. The texture is made in it and adopted by Skia (`Image.adoptTextureFrom`, the only
 * wrapping Skiko 0.150 has): Skia deletes it with the image. The framebuffer on it is Reflux's; both are kept across playbacks of
 * the same size and made again when the size changes. The render context belongs to the playback: made before the engine opens
 * anything ([attach]) and freed in a draw, with the context current, before the engine is closed ([release]), as libmpv requires.
 *
 * Reaching Skiko's context needs its internals (SkikoGl); where they differ, or Skiko does not draw with OpenGL, [available] is
 * false and the shell keeps the native video window (LinuxVideoShape), the fallback, and the log says so.
 */
internal class LinuxGlVideo(private val window: Component, private val log: (String) -> Unit = {}) {
    /** Read where the picture is drawn: a new frame changes it, so Compose draws the picture again, and nothing else. */
    var frames by mutableIntStateOf(0)
        private set

    /** Where the picture was last laid out, in the window's pixels (Compose's), for tests and diagnostics. */
    @Volatile var placed: androidx.compose.ui.geometry.Rect? = null

    private var player: MpvPlayer? = null
    private var render: MpvGlRender? = null
    private var context: DirectContext? = null
    private var image: Image? = null
    private var texture = 0
    private var framebuffer = 0
    private var width = 0
    private var height = 0
    private var releasing: CompletableDeferred<Unit>? = null
    private var attaching: CompletableDeferred<Boolean>? = null
    private val pending = AtomicBoolean(false)
    @Volatile private var failed = false

    /** Whether the picture can be drawn this way here: Skiko draws the window with OpenGL, and its context can be reached. */
    val available: Boolean get() = !failed && gl != null && SkikoGl.layer(window)?.let { SkikoGl.renderApi(it) } == "OPENGL"

    /** Why [available] is false, for the diagnostics log. */
    fun unavailableReason(): String = when {
        failed -> "drawing it in Reflux's scene failed earlier"
        gl == null -> "OpenGL (libGL) could not be loaded"
        SkikoGl.layer(window) == null -> "the window's Skia layer was not found (Skiko ${SkikoGl.version})"
        else -> "Skiko ${SkikoGl.version} draws with ${SkikoGl.layer(window)?.let { SkikoGl.renderApi(it) }}, not OpenGL"
    }

    /**
     * Shows [engine], created with `vo=libmpv` and given nothing to play yet: makes its render context in the next draw and
     * returns once it exists, true, or false when it could not be made (the log says why). It must exist **before** the engine
     * opens a file: libmpv's video output is set up as the file opens, and without a render context then it fails and the file
     * plays as sound alone (seen once on the real desktop, 2026-11-24). A window that draws nothing within [timeoutMs] is asked
     * to draw at once.
     */
    suspend fun attach(engine: MpvPlayer, timeoutMs: Long = 1_500): Boolean {
        val ready = CompletableDeferred<Boolean>()
        onEventThread {
            player = engine
            attaching = ready
            requestDraw()
        }
        withTimeoutOrNull(timeoutMs) { ready.await() }?.let { return it }
        onEventThread {
            frames++
            SkikoGl.renderImmediately(window)
        }
        val made = ready.isCompleted && ready.getCompleted()
        if (!made) onEventThread {
            if (!ready.isCompleted) log("$RENDERER: no draw came to make the render context within $timeoutMs ms")
            attaching = null
            freeNow(inDraw = false)
        }
        return made
    }

    /**
     * Frees the playback's render context at the next draw, with Skiko's context current, before the engine is closed; waits at
     * most [timeoutMs] for it, then asks Skiko to draw at once. Only a window that cannot draw at all (being disposed) has it
     * freed outside a draw, said in the log.
     */
    suspend fun release(timeoutMs: Long = 1_500) {
        if (onEventThread { player == null && render == null }) return
        val done = CompletableDeferred<Unit>()
        onEventThread {
            releasing = done
            requestDraw()
        }
        if (withTimeoutOrNull(timeoutMs) { done.await() } != null) return
        val forced = onEventThread {
            frames++
            SkikoGl.renderImmediately(window)
            !done.isActive
        }
        if (forced) {
            log("$RENDERER: released in a forced draw (none came within $timeoutMs ms)")
            return
        }
        log("$RENDERER: no draw could be made; released outside a draw")
        onEventThread {
            if (releasing === done) {
                freeNow(inDraw = false)
                releasing = null
                done.complete(Unit)
            }
        }
    }

    private fun <T> onEventThread(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun requestDraw() {
        if (pending.compareAndSet(false, true)) SwingUtilities.invokeLater {
            pending.set(false)
            frames++
        }
    }

    /**
     * Draws the current frame at [w] x [h] device pixels onto [canvas], rendering a new one first when the engine has one. Called
     * by Compose while Skiko draws the window, its OpenGL context current. Returns false when nothing could be drawn.
     */
    fun draw(canvas: org.jetbrains.skia.Canvas, w: Int, h: Int): Boolean {
        releasing?.let { done ->
            freeNow(inDraw = true)
            releasing = null
            done.complete(Unit)
            return false
        }
        val engine = player ?: return false
        draws++
        val gl = gl ?: return fail("OpenGL (libGL) could not be loaded")
        val skia = SkikoGl.directContext(window) ?: return fail("Skiko's OpenGL context was not reachable (Skiko ${SkikoGl.version})")
        // A context Skiko replaced takes the texture with it: everything is made again in the new one.
        if (context !== skia) {
            render?.free()
            render = null
            image?.close()
            image = null
            texture = 0
            framebuffer = 0
            context = skia
        }
        // A new render context (a new playback) renders at once, so the texture kept from the last one never shows its picture.
        val newPlayback = render == null
        if (newPlayback) {
            render = MpvGlRender.create(engine, getProcAddress, onUpdate)
                ?: return fail("libmpv's render context could not be made: ${MpvPlayer.lastFailure}")
            log("$RENDERER: libmpv render context made in Skiko's OpenGL context (Skiko ${SkikoGl.version}" +
                (if (SkikoGl.version != SkikoGl.TESTED_VERSION) ", untested; verified with ${SkikoGl.TESTED_VERSION}" else "") + ")")
        }
        val mpv = render!!
        attaching?.complete(true)
        attaching = null
        if (w <= 0 || h <= 0) return false
        var fresh = newPlayback
        if (image == null || w != width || h != height) {
            if (framebuffer != 0) gl.deleteFramebuffer(framebuffer)
            framebuffer = 0
            image?.close()
            image = makeTexture(gl, skia, w, h)
            width = w
            height = h
            if (!targetLogged) {
                targetLogged = true
                log("$RENDERER: drawing frames into a ${w}x$h texture through its framebuffer; Skia draws it in the window's scene")
            }
            fresh = true
        }
        if (framebuffer == 0) {
            framebuffer = gl.genFramebuffer()
            gl.bindFramebuffer(GL_FRAMEBUFFER, framebuffer)
            gl.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            val complete = gl.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE
            gl.bindFramebuffer(GL_FRAMEBUFFER, 0)
            skia.resetGLAll()
            if (!complete) return fail("the ${w}x$h framebuffer on the texture was not complete")
        }
        if (fresh || mpv.update() and MpvGlRender.UPDATE_FRAME != 0L) {
            val code = mpv.render(framebuffer, w, h, flipY = false)
            gl.bindFramebuffer(GL_FRAMEBUFFER, 0)
            // libmpv changed OpenGL's state behind Skia's back.
            skia.resetGLAll()
            mpv.reportSwap()
            if (code < 0 && !renderErrorLogged) {
                renderErrorLogged = true
                log("$RENDERER: libmpv could not render a frame: error $code")
            }
            rendered++
            if (rendered == REPORT_AFTER_FRAMES) log("$RENDERER: ${report(engine).also { lastReport = it }}")
            else if (rendered % REPORT_AFTER_FRAMES == 0) lastReport = report(engine)
        }
        image?.let { canvas.drawImage(it, 0f, 0f) }
        return true
    }

    /** The integrated picture cannot be drawn: said once, loudly; the next playback uses the native window. */
    private fun fail(reason: String): Boolean {
        if (!failed) log("$RENDERER failed: $reason. " + if (attaching != null) "This playback uses $FALLBACK." else "This playback shows no picture; the next one uses $FALLBACK.")
        failed = true
        attaching?.complete(false)
        attaching = null
        return false
    }

    private var targetLogged = false

    /** A [w] x [h] texture in Skiko's context (current), adopted by Skia: it is deleted with the image. */
    private fun makeTexture(gl: Gl, skia: DirectContext, w: Int, h: Int): Image {
        texture = gl.genTexture()
        gl.bindTexture(GL_TEXTURE_2D, texture)
        gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        gl.texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE)
        gl.bindTexture(GL_TEXTURE_2D, 0)
        skia.resetGLAll()
        return BackendTexture.makeGL(w, h, false, texture, GL_TEXTURE_2D, GL_RGBA8).use { backend ->
            Image.adoptTextureFrom(skia, backend, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888)
        }
    }

    private var renderErrorLogged = false
    private var rendered = 0
    private var lastReport: String? = null

    /** What decoded and what drew, from the engine itself, for the log: proof of the path, not an assumption about it. */
    private fun report(engine: MpvPlayer): String {
        val p = { name: String -> engine.property(name) ?: "-" }
        return "after $rendered frames: OpenGL ${SkikoGl.renderInfo(window)?.lines()?.joinToString("; ") { it.trim() }?.take(200)}; " +
            "vo=${p("current-vo")} hwdec=${p("hwdec")} hwdec-current=${p("hwdec-current")} codec=${p("video-codec")} " +
            "${p("video-params/w")}x${p("video-params/h")} pixelformat=${p("video-dec-params/pixelformat")} " +
            "hw-pixelformat=${p("video-dec-params/hw-pixelformat")} output=${p("video-params/pixelformat")} " +
            "colormatrix=${p("video-params/colormatrix")} levels=${p("video-params/colorlevels")} " +
            "dropped(vo)=${p("frame-drop-count")} dropped(decoder)=${p("decoder-frame-drop-count")} avsync=${p("avsync")}"
    }

    /** The totals at the end of a playback. */
    private fun engineReport() {
        if (player != null) log("$RENDERER: released after $rendered frames ($draws draws, ${updates.get()} updates from libmpv); last seen ${lastReport ?: "-"}")
        rendered = 0
        draws = 0
        updates.set(0)
        lastReport = null
        renderErrorLogged = false
    }

    /**
     * Ends the playback's part. [inDraw], Skiko's context is current: the render context is freed in it, and the texture and
     * framebuffer are kept for the next playback. Outside a draw (a window being disposed) libmpv frees without OpenGL current, and
     * the texture is let go for Skia to free with its context.
     */
    private fun freeNow(inDraw: Boolean) {
        engineReport()
        render?.free()
        render = null
        if (!inDraw) {
            image?.close()
            image = null
            texture = 0
            framebuffer = 0
            width = 0
            height = 0
            context = null
        }
        player = null
    }

    private val updates = AtomicInteger()
    private var draws = 0

    private val onUpdate = MpvGlRender.Update {
        updates.incrementAndGet()
        requestDraw()
    }

    private val getProcAddress = MpvGlRender.GetProcAddress { _, name -> gl?.procAddress(name) }

    /** OpenGL's functions as the current context has them (glXGetProcAddressARB), for the few calls made here. */
    private class Gl(private val glx: Glx) {
        private val functions = HashMap<String, Function>()
        private val scratch = Memory(4)
        fun procAddress(name: String): Pointer? = glx.glXGetProcAddressARB(name)
        private fun fn(name: String) = functions.getOrPut(name) { Function.getFunction(procAddress(name) ?: error("no $name")) }
        fun genTexture(): Int = scratch.also { fn("glGenTextures").invoke(arrayOf(1, it)) }.getInt(0)
        fun bindTexture(target: Int, id: Int) = fn("glBindTexture").invoke(arrayOf(target, id))
        fun texParameteri(target: Int, name: Int, value: Int) = fn("glTexParameteri").invoke(arrayOf(target, name, value))
        fun texImage2D(target: Int, level: Int, internal: Int, w: Int, h: Int, border: Int, format: Int, type: Int) =
            fn("glTexImage2D").invoke(arrayOf(target, level, internal, w, h, border, format, type, null))
        fun genFramebuffer(): Int = scratch.also { fn("glGenFramebuffers").invoke(arrayOf(1, it)) }.getInt(0)
        fun bindFramebuffer(target: Int, id: Int) = fn("glBindFramebuffer").invoke(arrayOf(target, id))
        fun framebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) =
            fn("glFramebufferTexture2D").invoke(arrayOf(target, attachment, texTarget, texture, level))
        fun checkFramebufferStatus(target: Int): Int = fn("glCheckFramebufferStatus").invokeInt(arrayOf(target))
        fun deleteFramebuffer(id: Int) = fn("glDeleteFramebuffers").invoke(arrayOf(1, scratch.apply { setInt(0, id) }))
    }

    @Suppress("FunctionName")
    private interface Glx : Library {
        fun glXGetProcAddressARB(name: String): Pointer?
    }

    companion object {
        private val gl: Gl? by lazy { runCatching { Gl(Native.load("GL", Glx::class.java)) }.getOrNull() }

        /** What the log calls the integrated picture, and the native-window fallback (LinuxVideoShape). */
        const val RENDERER = "linux-mpv-gl-integrated"
        const val FALLBACK = "linux-x11-shape-fallback"
        private const val REPORT_AFTER_FRAMES = 120

        const val GL_TEXTURE_2D = 0x0DE1
        const val GL_TEXTURE_MIN_FILTER = 0x2801
        const val GL_TEXTURE_MAG_FILTER = 0x2800
        const val GL_LINEAR = 0x2601
        const val GL_RGBA8 = 0x8058
        const val GL_RGBA = 0x1908
        const val GL_UNSIGNED_BYTE = 0x1401
        const val GL_FRAMEBUFFER = 0x8D40
        const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    }
}
