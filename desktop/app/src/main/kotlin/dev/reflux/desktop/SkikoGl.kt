package dev.reflux.desktop

import org.jetbrains.skia.DirectContext
import java.awt.Component
import java.awt.Container

/**
 * The one place Reflux reaches into Skiko's internals (B-084, LinuxGlVideo): the window's `SkiaLayer`, the graphics API it draws
 * with, and the Skia `DirectContext` of its OpenGL context. Skiko has no public way to these. The path is the one Skiko
 * [TESTED_VERSION] has (SkiaLayer → redrawerManager → getRedrawer() → contextHandler → context); every step is checked, and where
 * one is missing the answer is null, so the player keeps its native video window and the log says why. Nothing here is cached:
 * Skiko can replace its redrawer or context, and the caller asks again each time it draws.
 */
internal object SkikoGl {
    /** The Skiko this was written against and verified with. */
    const val TESTED_VERSION = "0.150.1"

    /** The Skiko this build runs with. */
    val version: String get() = runCatching { org.jetbrains.skiko.Version.skiko }.getOrDefault("unknown")

    /** The window's Skiko layer: Compose's `hierarchyRoot`, a `SkiaLayer` somewhere among the window's components. */
    fun layer(root: Component): Component? {
        if (isSkiaLayer(root.javaClass)) return root
        if (root is Container) for (child in root.components) layer(child)?.let { return it }
        return null
    }

    /** The graphics API the layer draws with (`OPENGL`, `SOFTWARE_FAST`, ...), by Skiko's own public getter; null if none. */
    fun renderApi(layer: Component): String? = runCatching { layer.javaClass.getMethod("getRenderApi").invoke(layer)?.toString() }.getOrNull()

    /** Skia's context for the window's OpenGL context, at this moment; null where Skiko is not drawing with OpenGL or has none yet. */
    fun directContext(root: Component): DirectContext? = runCatching {
        val layer = layer(root) ?: return null
        if (renderApi(layer) != "OPENGL") return null
        val manager = field(layer, "redrawerManager") ?: return null
        val redrawer = manager.javaClass.getMethod("getRedrawer").invoke(manager) ?: return null
        if (!redrawer.javaClass.name.endsWith("LinuxOpenGLRedrawer")) return null
        val handler = field(redrawer, "contextHandler") ?: return null
        field(handler, "context") as? DirectContext
    }.getOrNull()

    /** Skiko's own description of how it draws (its public `Redrawer.getRenderInfo`: the OpenGL vendor, renderer, version). */
    fun renderInfo(root: Component): String? = redrawer(root)?.let { r -> runCatching { r.javaClass.getMethod("getRenderInfo").invoke(r) as? String }.getOrNull() }

    /**
     * Draws the window now, on this (the event) thread, with Skiko's context current: Skiko's public `Redrawer.renderImmediately`,
     * for work that must happen in a draw when none is coming (a hidden or minimised window). False where it could not be asked.
     */
    fun renderImmediately(root: Component): Boolean = redrawer(root)?.let { r -> runCatching { r.javaClass.getMethod("renderImmediately").invoke(r); true }.getOrDefault(false) } ?: false

    private fun redrawer(root: Component): Any? = runCatching {
        val layer = layer(root) ?: return null
        val manager = field(layer, "redrawerManager") ?: return null
        manager.javaClass.getMethod("getRedrawer").invoke(manager)
    }.getOrNull()

    private fun isSkiaLayer(type: Class<*>?): Boolean = type != null && (type.name == "org.jetbrains.skiko.SkiaLayer" || isSkiaLayer(type.superclass))

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            type.declaredFields.firstOrNull { it.name == name }?.let { f -> f.isAccessible = true; return f.get(target) }
            type = type.superclass
        }
        return null
    }
}
