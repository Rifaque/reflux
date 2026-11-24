package dev.reflux.desktop

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CACHY-001: the Linux archive's `bin/install-desktop-entry` adds a desktop entry that names Reflux's window class, so a dock
 * shows Reflux's icon on its window and groups it with the entry; and Reflux names that class only where it applies.
 */
class LinuxDesktopEntryTest {
    private val script = File("packaging/linux/install-desktop-entry")

    private fun run(folder: File, data: File, vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf("sh", folder.resolve("bin/install-desktop-entry").path) + args).redirectErrorStream(true)
            .apply { environment()["XDG_DATA_HOME"] = data.path }.start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    private fun extracted(name: String): File {
        val folder = Files.createTempDirectory("reflux-entry").resolve(name).toFile()
        folder.resolve("bin").mkdirs()
        folder.resolve("lib").mkdirs()
        folder.resolve("bin/Reflux").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        folder.resolve("lib/Reflux.png").writeBytes(ByteArray(1))
        script.copyTo(folder.resolve("bin/install-desktop-entry"))
        return folder
    }

    @Test
    fun theEntryNamesTheWindowClassAndThisFolder() {
        assumeTrue("a POSIX shell", File("/bin/sh").canExecute())
        val data = Files.createTempDirectory("reflux-data").toFile()
        val folder = extracted("Reflux \$beta \"2\"")
        val (code, output) = run(folder, data)
        assertEquals(0, code, output)
        val entry = data.resolve("applications/reflux.desktop").readLines()
        assertTrue("StartupWMClass=$LINUX_WINDOW_CLASS" in entry, entry.toString())
        assertTrue("Name=Reflux" in entry)
        assertTrue("Icon=${folder.canonicalPath}/lib/Reflux.png" in entry, entry.toString())
        // Quoted as the specification says: \\ before $ and " (a string's escapes are read before the quoting).
        val dir = folder.canonicalPath.replace("$", "\\\\$").replace("\"", "\\\\\"")
        assertTrue("Exec=\"$dir/bin/Reflux\"" in entry, entry.toString())

        val (removed, _) = run(folder, data, "--remove")
        assertEquals(0, removed)
        assertFalse(data.resolve("applications/reflux.desktop").exists())
    }

    @Test
    fun aFolderMenusCannotStartFromIsRefusedWithAReason() {
        assumeTrue("a POSIX shell", File("/bin/sh").canExecute())
        val data = Files.createTempDirectory("reflux-data").toFile()
        val (code, output) = run(extracted("Reflux 100%"), data)
        assertEquals(1, code)
        assertTrue("contains a %" in output, output)
        assertFalse(data.resolve("applications/reflux.desktop").exists())
    }

    @Test
    fun theWindowClassIsNamedOnlyOnLinux() {
        assertFalse(nameTheWindowClass("Windows 11"))
        assertFalse(nameTheWindowClass("Mac OS X"))
    }
}
