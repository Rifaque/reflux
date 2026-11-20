package dev.reflux.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.util.concurrent.TimeUnit

/**
 * The display scale a Linux desktop asks of its X11 applications, which the Java toolkit does not apply (B-063).
 *
 * Reflux's window is an X11 one (through XWayland on a Wayland desktop). Java 21's X11 toolkit scales only by a whole
 * GDK_SCALE: it ignores the desktop's Xft.dpi, and rounds a fractional `sun.java2d.uiScale` down to a whole number
 * (measured with Java 21.0.13 under Xvfb: 1.5 gave 1.0). So on a desktop set to 125%, 150% or 175%, Reflux was drawn at
 * 100%: small where the desktop let applications scale themselves (KDE's "Apply scaling themselves", its default), and
 * enlarged as a picture, blurred, where the compositor scales X11 applications instead.
 *
 * GTK and Qt applications take their scale from Xft.dpi (96 is 100%), as Compose can: Reflux draws its content at that
 * density in the window's own pixels, so text and edges are drawn at the display's resolution rather than enlarged. The
 * toolkit's whole scale, where there is one, is kept and only the rest is added, so nothing is scaled twice; GNOME's
 * text-scaling-factor, which GNOME folds into Xft.dpi and Reflux already applies to text alone, is taken out of it.
 * REFLUX_SCALE (for example 1.5) sets the scale outright, for a desktop that says nothing.
 */
internal object LinuxDisplayScale {
    /** What is applied on top of the toolkit's scale, 1 where nothing is: read once, before the first window. */
    val extra: Float by lazy { runCatching { measure() }.getOrDefault(1f) }

    private fun measure(): Float {
        if (!System.getProperty("os.name").orEmpty().startsWith("Linux") || GraphicsEnvironment.isHeadless()) return 1f
        val toolkit = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        val override = System.getenv("REFLUX_SCALE")?.toFloatOrNull()
        val explicit = System.getenv("GDK_SCALE") != null || System.getProperty("sun.java2d.uiScale") != null
        return contentScale(
            xftDpi = if (override != null || explicit) null else xftDpi(),
            toolkitScale = toolkit,
            textScale = if (override != null || explicit) 1.0 else gnomeTextScale(),
            override = override,
        )
    }

    /**
     * The scale Reflux adds to the toolkit's [toolkitScale]: [override] where it is given (as the whole scale); else the
     * desktop's [xftDpi] over 96, less its [textScale], over the toolkit's. 1 where that is under 5% more, or unknown.
     */
    fun contentScale(xftDpi: Double?, toolkitScale: Double, textScale: Double = 1.0, override: Float? = null): Float {
        val whole = override?.toDouble() ?: (xftDpi?.let { it / 96.0 / textScale.coerceAtLeast(0.5) } ?: return 1f)
        val extra = whole / toolkitScale.coerceAtLeast(1.0)
        if (extra.isNaN() || extra < 1.05) return 1f
        // To the nearest 1/8, like the desktops' own steps (125%, 150%, 175%, 200%): no odd scale from a rounded DPI.
        return (Math.round(extra.coerceAtMost(4.0) * 8) / 8.0).toFloat()
    }

    /** Xft.dpi from the desktop's settings daemon (XSETTINGS, in 1024ths), else from its X resources; null if neither says. */
    private fun xftDpi(): Double? {
        (Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI") as? Int)?.takeIf { it > 0 }?.let { return it / 1024.0 }
        val resources = run("xrdb", "-query") ?: return null
        return parseXftDpi(resources)
    }

    /** The Xft.dpi line of `xrdb -query`'s output. */
    fun parseXftDpi(resources: String): Double? =
        resources.lineSequence().firstOrNull { it.trimStart().startsWith("Xft.dpi:") }?.substringAfter(':')?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }

    private fun gnomeTextScale(): Double =
        run("gsettings", "get", "org.gnome.desktop.interface", "text-scaling-factor")?.trim()?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return null
        }
        process.inputStream.bufferedReader().readText().takeIf { process.exitValue() == 0 }
    }.getOrNull()
}

/** [content] at the desktop's scale where the toolkit left some of it out ([LinuxDisplayScale.extra]); as it is elsewhere. */
@Composable
internal fun AtDisplayScale(content: @Composable () -> Unit) {
    val extra = LinuxDisplayScale.extra
    if (extra == 1f) return content()
    val toolkit = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(toolkit.density * extra, toolkit.fontScale), content = content)
}
