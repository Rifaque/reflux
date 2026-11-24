package dev.reflux.playback.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * libmpv's render API for OpenGL (`render.h`, `render_gl.h`): the engine draws each frame into a framebuffer the application
 * owns, in the application's own OpenGL context, instead of into a native window of its own (B-084: the Linux desktop's
 * player, where the frame is part of the same scene as the controls).
 *
 * Every call but [create]'s callbacks must be made on the one thread where the OpenGL context is current, with it current:
 * that is libmpv's rule. The engine must have been created with `vo=libmpv`. [free] before the engine is closed.
 */
class MpvGlRender private constructor(private val api: RenderApi, private var context: Pointer?, private val retained: List<Any>) {
    /** `void *(*get_proc_address)(void *ctx, const char *name)`: how the engine finds OpenGL's functions. */
    fun interface GetProcAddress : Callback {
        fun invoke(ctx: Pointer?, name: String): Pointer?
    }

    /** `void (*mpv_render_update_fn)(void *cb_ctx)`: a frame is ready, or something else needs a render. Any thread. */
    fun interface Update : Callback {
        fun invoke(ctx: Pointer?)
    }

    @Suppress("FunctionName")
    internal interface RenderApi : Library {
        fun mpv_render_context_create(result: Memory, mpv: Pointer, params: Pointer): Int
        fun mpv_render_context_set_update_callback(ctx: Pointer, callback: Update?, callbackCtx: Pointer?)
        fun mpv_render_context_update(ctx: Pointer): Long
        fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
        fun mpv_render_context_report_swap(ctx: Pointer)
        fun mpv_render_context_free(ctx: Pointer)
    }

    /** Whether the context is still there. */
    val alive: Boolean get() = context != null

    /** What the engine needs done: [UPDATE_FRAME] set when a new frame is to be rendered. */
    fun update(): Long = context?.let(api::mpv_render_context_update) ?: 0L

    /**
     * Renders the current frame into framebuffer [fbo] of [width] x [height] pixels, never waiting for the frame's display
     * time (`BLOCK_FOR_TARGET_TIME` 0: the caller is the toolkit's drawing thread). Without [flipY] the frame's top row is the
     * framebuffer's row 0, so its texture is read with its origin at the top left (verified with a test card's timecode,
     * 2026-11-24); [flipY] is for the window's own framebuffer. Returns libmpv's error code.
     */
    fun render(fbo: Int, width: Int, height: Int, flipY: Boolean): Int {
        val ctx = context ?: return -1
        val fboStruct = Memory(16).apply { setInt(0, fbo); setInt(4, width); setInt(8, height); setInt(12, 0) }
        val flip = Memory(4).apply { setInt(0, if (flipY) 1 else 0) }
        val block = Memory(4).apply { setInt(0, 0) }
        val params = params(PARAM_OPENGL_FBO to fboStruct, PARAM_FLIP_Y to flip, PARAM_BLOCK_FOR_TARGET_TIME to block)
        return api.mpv_render_context_render(ctx, params)
    }

    /** Tells the engine the frame went to the screen, for its timing. */
    fun reportSwap() {
        context?.let(api::mpv_render_context_report_swap)
    }

    /** Frees the context and the OpenGL objects it made: with the context current, before the engine is destroyed. */
    fun free() {
        val ctx = context ?: return
        context = null
        api.mpv_render_context_set_update_callback(ctx, null, null)
        api.mpv_render_context_free(ctx)
        retained.size
    }

    companion object {
        const val UPDATE_FRAME = 1L
        private const val PARAM_API_TYPE = 1
        private const val PARAM_OPENGL_INIT_PARAMS = 2
        private const val PARAM_OPENGL_FBO = 3
        private const val PARAM_FLIP_Y = 4
        private const val PARAM_X11_DISPLAY = 8
        private const val PARAM_BLOCK_FOR_TARGET_TIME = 12

        /** A `mpv_render_param` array: (type, data) pairs and the terminating zero entry. */
        private fun params(vararg entries: Pair<Int, Pointer?>): Pointer {
            val size = Native.POINTER_SIZE * 2
            return Memory((size * (entries.size + 1)).toLong()).apply {
                clear()
                entries.forEachIndexed { i, (type, data) ->
                    setInt((i * size).toLong(), type)
                    setPointer((i * size + Native.POINTER_SIZE).toLong(), data)
                }
            }
        }

        /**
         * Makes a render context for [player] in the OpenGL context current on this thread, or null where libmpv refuses (its
         * reason in [MpvPlayer.lastFailure]). [getProcAddress] finds OpenGL functions in that context; [onUpdate] is called on
         * libmpv's own thread when there is something to render, and must only ask for a redraw. [x11Display], an Xlib
         * `Display*`, lets the engine use hardware decoding interop that needs one.
         */
        fun create(player: MpvPlayer, getProcAddress: GetProcAddress, onUpdate: Update, x11Display: Pointer? = null): MpvGlRender? {
            val api = runCatching { Native.load(player.libraryName, RenderApi::class.java) }.getOrNull() ?: return null
            val apiType = Memory(7).apply { setString(0, "opengl", "US-ASCII") }
            val init = Memory((Native.POINTER_SIZE * 2).toLong()).apply {
                setPointer(0, com.sun.jna.CallbackReference.getFunctionPointer(getProcAddress))
                setPointer(Native.POINTER_SIZE.toLong(), null)
            }
            val entries = buildList {
                add(PARAM_API_TYPE to apiType)
                add(PARAM_OPENGL_INIT_PARAMS to init)
                if (x11Display != null) add(PARAM_X11_DISPLAY to x11Display)
            }
            val result = Memory(Native.POINTER_SIZE.toLong()).apply { clear() }
            val code = api.mpv_render_context_create(result, player.handlePointer, params(*entries.toTypedArray()))
            if (code < 0) {
                MpvPlayer.lastFailure = "libmpv's render context could not be made: error $code"
                return null
            }
            val ctx = result.getPointer(0) ?: return null
            api.mpv_render_context_set_update_callback(ctx, onUpdate, null)
            // The callbacks are kept reachable for as long as the context is: libmpv holds only their addresses.
            return MpvGlRender(api, ctx, listOf(getProcAddress, onUpdate, apiType, init))
        }
    }
}
