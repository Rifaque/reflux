package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import dev.reflux.ui.state.AccessibilityPreferences
import java.util.concurrent.TimeUnit

/**
 * The system's accessibility settings, which the desktop toolkit does not read for itself. Whatever a system
 * does not say leaves the default: a missing setting is never an error.
 */
internal object DesktopAccessibility {
    fun read(): AccessibilityPreferences {
        val os = System.getProperty("os.name")
        return when {
            os.startsWith("Windows") -> windows()
            os.startsWith("Linux") -> linux()
            else -> AccessibilityPreferences()
        }
    }

    private fun windows(): AccessibilityPreferences {
        val user32 = Native.load("user32", User32::class.java)
        fun flag(action: Int, otherwise: Boolean): Boolean {
            val value = IntByReference()
            return if (user32.SystemParametersInfoW(action, 0, value, 0)) value.value != 0 else otherwise
        }
        return AccessibilityPreferences(
            screenReader = flag(SPI_GETSCREENREADER, otherwise = false),
            reduceMotion = !flag(SPI_GETCLIENTAREAANIMATION, otherwise = true),
            textScale = run("reg", "query", "HKCU\\SOFTWARE\\Microsoft\\Accessibility", "/v", "TextScaleFactor")?.let(::windowsTextScale) ?: 1f,
        )
    }

    private fun linux(): AccessibilityPreferences = AccessibilityPreferences(
        screenReader = gsetting("org.gnome.desktop.a11y.applications", "screen-reader-enabled") == "true",
        reduceMotion = gsetting("org.gnome.desktop.interface", "enable-animations") == "false",
        textScale = gsetting("org.gnome.desktop.interface", "text-scaling-factor")?.let(::linuxTextScale) ?: 1f,
    )

    /** "    TextScaleFactor    REG_DWORD    0x96" (a percentage, 100 to 225) to 1.5. Null when it is not that. */
    fun windowsTextScale(output: String): Float? =
        Regex("""REG_DWORD\s+0x([0-9a-fA-F]+)""").find(output)?.groupValues?.get(1)?.toIntOrNull(16)?.let { it / 100f }?.takeIf(::plausible)

    /** "1.25" to 1.25. Null when it is not that. */
    fun linuxTextScale(output: String): Float? = output.trim().toFloatOrNull()?.takeIf(::plausible)

    private fun plausible(scale: Float) = scale in 0.5f..3f

    private fun gsetting(schema: String, key: String): String? = run("gsettings", "get", schema, key)?.trim()

    /** What [command] printed, or null if it is not there, failed, or did not finish promptly. */
    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val finished = process.waitFor(COMMAND_PATIENCE_MS, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        process.inputStream.bufferedReader().readText().takeIf { finished && process.exitValue() == 0 }
    }.getOrNull()

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun SystemParametersInfoW(action: Int, param: Int, value: IntByReference, winIni: Int): Boolean
    }

    private const val COMMAND_PATIENCE_MS = 2_000L
    private const val SPI_GETSCREENREADER = 0x0046
    private const val SPI_GETCLIENTAREAANIMATION = 0x1042
}
