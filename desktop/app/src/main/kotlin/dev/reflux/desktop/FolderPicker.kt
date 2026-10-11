package dev.reflux.desktop

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Lets the user choose a media folder with the platform's own folder dialog: the shell dialog on Windows,
 * the desktop's dialog on Linux (GNOME's or KDE's), and the toolkit's chooser when neither can be shown.
 */
internal object FolderPicker {
    const val TITLE = "Choose a folder with movies or shows"

    /** Returns the chosen folder, or null when the dialog was cancelled. */
    suspend fun choose(owner: Window?): Path? = withContext(Dispatchers.IO) {
        // Every dialog here is modal and blocks its caller, so none of them may run on the UI thread.
        val native = if (System.getProperty("os.name").startsWith("Windows")) {
            runCatching { Shown(WindowsFolderDialog.show(owner, TITLE)) }.getOrNull()
        } else {
            DesktopFolderDialog.show(TITLE)
        }
        (native ?: Shown(toolkit(owner))).folder
    }

    private fun toolkit(owner: Window?): Path? {
        var result: Path? = null
        SwingUtilities.invokeAndWait {
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
            val chooser = JFileChooser().apply {
                dialogTitle = TITLE
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) result = chooser.selectedFile?.toPath()
        }
        return result
    }
}

/** A dialog that was shown: the folder chosen in it, or null when the user cancelled. */
internal class Shown(val folder: Path?)

/**
 * The folder dialog of the Linux desktop in use, through the helper each desktop ships: `kdialog` on KDE,
 * `zenity` on GNOME and most others.
 */
internal object DesktopFolderDialog {
    /** Shows the dialog and waits for it. Returns null when no helper is installed, so another dialog can be used. */
    fun show(
        title: String,
        desktop: String = System.getenv("XDG_CURRENT_DESKTOP").orEmpty(),
        home: String = System.getProperty("user.home"),
        run: (List<String>) -> Pair<Int, String>? = ::run,
    ): Shown? {
        val zenity = listOf("zenity", "--file-selection", "--directory", "--title=$title")
        val kdialog = listOf("kdialog", "--title", title, "--getexistingdirectory", home)
        val helpers = if (desktop.contains("KDE", ignoreCase = true)) listOf(kdialog, zenity) else listOf(zenity, kdialog)
        for (command in helpers) {
            val (exit, output) = run(command) ?: continue
            return when (exit) {
                0 -> Shown(output.lineSequence().firstOrNull { it.isNotBlank() }?.let { Path.of(it.trim()) })
                1 -> Shown(null) // cancelled
                else -> continue // the helper could not open a dialog (no display, missing toolkit)
            }
        }
        return null
    }

    /** Runs [command] and returns its exit code and output, or null when the program is not installed. */
    private fun run(command: List<String>): Pair<Int, String>? = try {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor() to output
    } catch (_: IOException) {
        null
    }
}

/** `IFileOpenDialog` in folder-picking mode, called through its COM vtable. */
internal object WindowsFolderDialog {
    @Suppress("FunctionName")
    private interface Ole32 : Library {
        fun CoInitializeEx(reserved: Pointer?, model: Int): Int
        fun CoUninitialize()
        fun CoCreateInstance(classId: Pointer, outer: Pointer?, context: Int, interfaceId: Pointer, result: PointerByReference): Int
        fun CoTaskMemFree(memory: Pointer?)
    }

    private val ole32: Ole32 by lazy { Native.load("ole32", Ole32::class.java) }

    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val CLSCTX_INPROC_SERVER = 0x1
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
    private const val CANCELLED = 0x800704C7.toInt() // HRESULT_FROM_WIN32(ERROR_CANCELLED)

    // Vtable slots: IUnknown (0..2), IModalWindow::Show (3), then IFileDialog and IShellItem methods.
    private const val RELEASE = 2
    private const val SHOW = 3
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val SET_TITLE = 17
    private const val GET_RESULT = 20
    private const val GET_DISPLAY_NAME = 5

    fun show(owner: Window?, title: String): Path? = withDialog(title) { dialog ->
        val shown = dialog.call(SHOW, owner?.let { Native.getWindowPointer(it) })
        if (shown == CANCELLED) return@withDialog null
        check(shown >= 0) { "The folder dialog could not be shown" }
        val itemRef = PointerByReference()
        if (dialog.call(GET_RESULT, itemRef) < 0) return@withDialog null
        val item = itemRef.value
        try {
            val nameRef = PointerByReference()
            if (item.call(GET_DISPLAY_NAME, SIGDN_FILESYSPATH, nameRef) < 0) return@withDialog null
            val path = nameRef.value.getWideString(0)
            ole32.CoTaskMemFree(nameRef.value)
            Path.of(path)
        } finally {
            item.call(RELEASE)
        }
    }

    /** Whether the shell dialog can be created and configured on this machine, without showing it. */
    fun available(): Boolean = runCatching { withDialog("") { true } }.getOrDefault(false)

    /** Creates the dialog in folder-picking mode on the calling thread, and releases it afterwards. */
    private fun <T> withDialog(title: String, use: (Pointer) -> T): T {
        val initialized = ole32.CoInitializeEx(null, COINIT_APARTMENTTHREADED) >= 0
        try {
            val dialogRef = PointerByReference()
            val created = ole32.CoCreateInstance(
                guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7"), null, CLSCTX_INPROC_SERVER,
                guid("D57C7288-D4AD-4768-BE02-9D969532D960"), dialogRef,
            )
            check(created >= 0) { "The folder dialog is unavailable" }
            val dialog = dialogRef.value
            try {
                val options = IntByReference()
                check(dialog.call(GET_OPTIONS, options) >= 0) { "The folder dialog cannot be configured" }
                check(dialog.call(SET_OPTIONS, options.value or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM) >= 0) { "The folder dialog cannot pick folders" }
                dialog.call(SET_TITLE, WString(title))
                return use(dialog)
            } finally {
                dialog.call(RELEASE)
            }
        } finally {
            if (initialized) ole32.CoUninitialize()
        }
    }

    /** Calls method [slot] of a COM object; returns its HRESULT. */
    private fun Pointer.call(slot: Int, vararg arguments: Any?): Int {
        val method = getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf(this, *arguments))
    }

    /** A GUID in its in-memory layout: three little-endian fields followed by eight bytes. */
    private fun guid(text: String): Memory {
        val hex = text.replace("-", "")
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(hex.substring(0, 8).toLong(16).toInt())
        buffer.putShort(hex.substring(8, 12).toInt(16).toShort())
        buffer.putShort(hex.substring(12, 16).toInt(16).toShort())
        for (i in 16 until 32 step 2) buffer.put(hex.substring(i, i + 2).toInt(16).toByte())
        return Memory(16).apply { write(0, buffer.array(), 0, 16) }
    }
}
