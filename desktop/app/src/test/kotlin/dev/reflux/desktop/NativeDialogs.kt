package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Operates the folder dialog for end-to-end tests, the way a person would but without their pointer: the
 * Windows shell dialog through window messages, and the toolkit's chooser in process.
 *
 * The GTK and KDE dialogs that [DesktopFolderDialog] prefers on Linux are separate programs whose text
 * fields auto-complete, which makes synthetic typing unreliable; the Linux end-to-end run therefore uses a
 * desktop without those helpers, and their handling is covered by `DesktopShellTest`.
 */
internal object NativeDialogs {
    /**
     * Waits for the folder dialog titled [title], then chooses [folder] in it (or cancels it when null).
     * A dialog that has only just appeared may not be ready to be answered, so the answer is repeated until
     * the dialog has actually gone.
     */
    fun answer(title: String, folder: Path?, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var seen = false
        while (System.currentTimeMillis() < deadline) {
            if (if (isWindows) WindowsDialog.answer(title, folder) else answerChooser(folder)) {
                seen = true
                val closing = System.currentTimeMillis() + 2_500
                while (System.currentTimeMillis() < closing) {
                    if (!isOpen(title)) return
                    Thread.sleep(100)
                }
            } else if (seen) {
                return // it closed between two looks
            }
            Thread.sleep(150)
        }
        error(if (seen) "The folder dialog \"$title\" did not accept the answer. ${if (isWindows) WindowsDialog.describe(title) else ""}" else "The folder dialog \"$title\" did not appear")
    }

    /** Whether the folder dialog is open right now. */
    fun isOpen(title: String): Boolean = if (isWindows) WindowsDialog.find(title) != null else onUiThread { chooser() != null }

    private fun answerChooser(folder: Path?): Boolean = onUiThread {
        val chooser = chooser() ?: return@onUiThread false
        if (folder == null) {
            chooser.cancelSelection()
        } else {
            chooser.selectedFile = folder.toFile()
            chooser.approveSelection()
        }
        true
    }

    private fun chooser(): JFileChooser? = Window.getWindows().filter { it.isShowing }.firstNotNullOfOrNull { find(it) }

    private fun find(root: Component): JFileChooser? = root as? JFileChooser ?: (root as? Container)?.components?.firstNotNullOfOrNull { find(it) }

    private fun <T> onUiThread(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: T? = null
        SwingUtilities.invokeAndWait { result = action() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

/** The Windows shell's folder dialog (`IFileOpenDialog`), operated through the messages its controls understand. */
private object WindowsDialog {
    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun FindWindowW(className: WString?, title: WString?): Pointer?
        fun EnumChildWindows(parent: Pointer, callback: Callback, data: Pointer?): Boolean
        fun GetClassNameW(window: Pointer, name: CharArray, length: Int): Int
        fun GetDlgCtrlID(window: Pointer): Int
        fun IsWindowVisible(window: Pointer): Boolean
        fun GetWindowThreadProcessId(window: Pointer, process: IntArray): Int
        fun GetWindowTextW(window: Pointer, text: CharArray, length: Int): Int
        fun SendMessageW(window: Pointer, message: Int, wParam: Pointer?, lParam: WString?): Pointer?
        fun PostMessageW(window: Pointer, message: Int, wParam: Pointer?, lParam: Pointer?): Boolean

        fun interface Callback : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
            fun invoke(window: Pointer, data: Pointer?): Boolean
        }
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun GetCurrentProcessId(): Int
    }

    private val user32: User32 by lazy { Native.load("user32", User32::class.java) }
    private val kernel32: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

    private const val WM_SETTEXT = 0x000C
    private const val WM_COMMAND = 0x0111
    private const val WM_CLOSE = 0x0010
    private const val IDOK = 1L

    /** This process's dialog titled [title], if it is showing. */
    fun find(title: String): Pointer? {
        val dialog = user32.FindWindowW(WString("#32770"), WString(title)) ?: return null
        val process = IntArray(1)
        user32.GetWindowThreadProcessId(dialog, process)
        return dialog.takeIf { process[0] == kernel32.GetCurrentProcessId() && user32.IsWindowVisible(dialog) }
    }

    fun answer(title: String, folder: Path?): Boolean {
        val dialog = find(title) ?: return false
        if (folder == null) {
            user32.PostMessageW(dialog, WM_CLOSE, null, null)
            return true
        }
        // The "Folder:" field is the dialog's one visible edit control outside the address bar.
        val edits = mutableListOf<Pointer>()
        var address: String? = null
        user32.EnumChildWindows(dialog, { child, _ ->
            if (user32.IsWindowVisible(child)) {
                if (className(child) == "Edit") edits += child
                text(child).takeIf { it.startsWith(ADDRESS) }?.let { address = it.removePrefix(ADDRESS) }
            }
            true
        }, null)
        val field = edits.firstOrNull { user32.GetDlgCtrlID(it) in FOLDER_FIELD_IDS } ?: edits.lastOrNull() ?: return false
        Thread.sleep(400) // the dialog finishes populating itself after it becomes visible
        // As for a person: a typed path takes the dialog into that folder, and "Select Folder" with
        // nothing typed then chooses the folder being shown.
        val inside = address?.let { runCatching { Path.of(it).toRealPath() == folder.toRealPath() }.getOrDefault(false) } == true
        user32.SendMessageW(field, WM_SETTEXT, null, WString(if (inside) "" else folder.toString()))
        user32.PostMessageW(dialog, WM_COMMAND, Pointer.createConstant(IDOK), null)
        return true
    }

    private fun className(window: Pointer): String = CharArray(64).let { String(it, 0, user32.GetClassNameW(window, it, it.size)) }

    private fun text(window: Pointer): String = CharArray(520).let { String(it, 0, user32.GetWindowTextW(window, it, it.size)) }

    /** How the dialog labels the toolbar that shows where it is. */
    private const val ADDRESS = "Address: "

    /** What the dialog consists of right now, for explaining a failure to operate it. */
    fun describe(title: String): String {
        val dialog = find(title) ?: return "It is no longer open."
        val children = mutableListOf<String>()
        user32.EnumChildWindows(dialog, { child, _ ->
            val name = CharArray(64)
            val text = CharArray(120)
            val kind = String(name, 0, user32.GetClassNameW(child, name, name.size))
            val label = String(text, 0, user32.GetWindowTextW(child, text, text.size))
            if (user32.IsWindowVisible(child) && (kind in setOf("Edit", "Button", "Static", "ComboBoxEx32") || label.isNotBlank())) {
                children += "$kind#${user32.GetDlgCtrlID(child)}[$label]"
            }
            true
        }, null)
        return "Its visible controls: ${children.take(40)}"
    }

    /** The control IDs the common dialogs give their file-name field. */
    private val FOLDER_FIELD_IDS = setOf(0x480, 0x47C)
}
