package dev.reflux.desktop

import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Reflux's window class on X11 and XWayland (CACHY-001): what a desktop entry's `StartupWMClass` names, and what docks,
 * task bars and compositor rules match a window by. The same in every build and launch, and the name of the desktop
 * entry Linux archives install (`bin/install-desktop-entry`).
 */
internal const val LINUX_WINDOW_CLASS = "reflux"

/**
 * Names Reflux's windows [LINUX_WINDOW_CLASS] on X11. Left alone, the toolkit names them after the Java class that started the
 * process (`dev-reflux-desktop-MainKt`, or a test runner's), which no desktop entry names: docks showed a generic icon and never
 * grouped Reflux with its launcher. The toolkit has no setting for it, only this field, which a window reads when it is
 * created; it needs `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` (the packaged launcher has it). Without that, or on
 * another toolkit, nothing changes and Reflux runs as before.
 *
 * @return whether the class was set.
 */
internal fun nameTheWindowClass(os: String = System.getProperty("os.name").orEmpty()): Boolean {
    if (!os.startsWith("Linux") || GraphicsEnvironment.isHeadless()) return false
    return runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return false
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, LINUX_WINDOW_CLASS)
        true
    }.getOrDefault(false)
}
