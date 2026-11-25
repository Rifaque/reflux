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

    /** Reflux's own entry (LinuxDesktopEntry) is the script's, byte for byte, for the same folder: the two never disagree. */
    @Test
    fun refluxWritesTheSameEntryAsTheScript() {
        assumeTrue("a POSIX shell", File("/bin/sh").canExecute())
        val data = Files.createTempDirectory("reflux-data").toFile()
        val folder = extracted("Reflux \$beta \"2\" `x` back\\slash")
        val (code, output) = run(folder, data)
        assertEquals(0, code, output)
        assertEquals(data.resolve("applications/reflux.desktop").readText(), LinuxDesktopEntry.entry(folder.canonicalFile.toPath()))
        assertTrue(LinuxDesktopEntry.MANAGED in data.resolve("applications/reflux.desktop").readText())
    }

    @Test
    fun aPackagedRefluxKeepsItsEntryAndAFolderLauncher() {
        val home = Files.createTempDirectory("reflux-home")
        val refluxData = Files.createTempDirectory("reflux-own")
        val folder = extracted("Reflux").canonicalFile.toPath()
        val launcher = folder.resolve("bin/Reflux")
        val target = home.resolve("applications/reflux.desktop")
        // Not from a package, or not on Linux: nothing is written.
        assertEquals(null, LinuxDesktopEntry.keep(null, home, refluxData, os = "Linux"))
        assertEquals(null, LinuxDesktopEntry.keep(launcher, home, refluxData, os = "Windows 11"))
        assertFalse(Files.exists(target))
        // The first start adds both.
        val first = LinuxDesktopEntry.keep(launcher, home, refluxData, os = "Linux")
        assertTrue(first.orEmpty().contains("added"), first)
        assertEquals(LinuxDesktopEntry.entry(folder), Files.readString(target))
        assertEquals(LinuxDesktopEntry.entry(folder), Files.readString(folder.resolve("Reflux.desktop")))
        // The next start changes nothing.
        assertEquals(null, LinuxDesktopEntry.keep(launcher, home, refluxData, os = "Linux"))
        // Moved: its own entry follows the folder.
        val moved = Files.move(folder, folder.resolveSibling("Reflux moved"))
        val after = LinuxDesktopEntry.keep(moved.resolve("bin/Reflux"), home, refluxData, os = "Linux")
        assertTrue(after.orEmpty().contains("updated"), after)
        assertTrue("Exec=\"$moved/bin/Reflux\"" in Files.readString(target))
        // Removed by the person (install-desktop-entry --remove): not put back.
        Files.delete(target)
        LinuxDesktopEntry.keep(moved.resolve("bin/Reflux"), home, refluxData, os = "Linux")
        assertFalse(Files.exists(target), "a removed entry stays removed")
        // Someone's own entry (not marked as Reflux's): left as it is.
        val own = "[Desktop Entry]\nName=My Reflux\n"
        Files.writeString(target, own)
        LinuxDesktopEntry.keep(moved.resolve("bin/Reflux"), home, refluxData, os = "Linux")
        assertEquals(own, Files.readString(target))
    }

    @Test
    fun aFolderAMenuCannotStartFromGetsNoEntry() {
        assertEquals(null, LinuxDesktopEntry.entry(java.nio.file.Path.of("/opt/Reflux 100%")))
    }

    @Test
    fun theWindowClassIsNamedOnlyOnLinux() {
        assertFalse(nameTheWindowClass("Windows 11"))
        assertFalse(nameTheWindowClass("Mac OS X"))
    }
}
