package dev.reflux.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reflux's own desktop entry on Linux, kept by Reflux itself (the owner's request in the final CachyOS session, 2026-11-25):
 * a dock or task bar on Wayland finds a window's icon only through a desktop entry named for its app id ([LINUX_WINDOW_CLASS]),
 * never from the window, and a file manager shows no icon for a Linux executable. Without the entry the dock showed a generic
 * icon for Reflux until `bin/install-desktop-entry` had been run (measured on Hyprland with Noctalia's dock). So a packaged
 * Reflux writes the same entry that script writes, at every start:
 *
 * - `<data home>/applications/reflux.desktop`, for menus, docks and launchers: written when there is none and Reflux has not
 *   written one before, or rewritten when it is Reflux's own ([MANAGED], the script writes it too) and the folder has moved.
 *   An entry without [MANAGED] (made or edited by someone) is left as it is; one Reflux wrote and someone then removed
 *   (`install-desktop-entry --remove`) is not put back ([INSTALLED_MARK] in Reflux's data folder says it was written once).
 * - `Reflux.desktop` in the extracted folder itself, beside `bin`, so the file manager shows Reflux's logo where it was unpacked
 *   (a folder that cannot be written, such as one installed by a package, is left alone).
 *
 * Only a packaged Reflux does this (`jpackage.app-path` is the launcher's path); a build run from Gradle or a test never writes
 * outside its own data folder. Everything is written atomically; a failure is only logged.
 */
internal object LinuxDesktopEntry {
    const val MANAGED = "X-Reflux-Managed=true"
    const val INSTALLED_MARK = "desktop-entry-installed"

    /** What was done, for the log; null when nothing was written. */
    fun keep(launcher: Path?, dataHome: Path, refluxData: Path, os: String = System.getProperty("os.name").orEmpty()): String? {
        if (!os.startsWith("Linux") || launcher == null) return null
        val folder = launcher.toAbsolutePath().parent?.parent ?: return null
        val icon = folder.resolve("lib/Reflux.png")
        if (!Files.isRegularFile(icon)) return null
        val text = entry(folder) ?: return "Reflux's desktop entry was not written: its folder's path ($folder) cannot be started from a menu"
        val done = mutableListOf<String>()
        val applications = dataHome.resolve("applications")
        val target = applications.resolve("$LINUX_WINDOW_CLASS.desktop")
        val mark = refluxData.resolve(INSTALLED_MARK)
        val existing = runCatching { Files.readString(target) }.getOrNull()
        when {
            existing == text -> {}
            existing == null && Files.exists(mark) -> {}
            existing != null && MANAGED !in existing -> {}
            else -> runCatching {
                write(target, text)
                Files.writeString(mark, folder.toString())
                done += if (existing == null) "added $target" else "updated $target for $folder"
            }.onFailure { done += "could not write $target: ${it.message}" }
        }
        val beside = folder.resolve("Reflux.desktop")
        if (runCatching { Files.readString(beside) }.getOrNull() != text && Files.isWritable(folder)) {
            runCatching { write(beside, text); beside.toFile().setExecutable(true, false) }
                .onSuccess { done += "added $beside" }
        }
        return done.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "Reflux's desktop entry: ")
    }

    /** The entry for Reflux unpacked in [folder], as `bin/install-desktop-entry` writes it; null where a menu could not start it. */
    fun entry(folder: Path): String? {
        val path = folder.toString()
        if ('%' in path || '\n' in path || '\r' in path) return null
        return """
            [Desktop Entry]
            Type=Application
            Name=Reflux
            GenericName=Media Player
            Comment=Play and organise your own films and shows
            Exec=${quote("$path/bin/Reflux")}
            Icon=${plain("$path/lib/Reflux.png")}
            Terminal=false
            StartupNotify=false
            StartupWMClass=$LINUX_WINDOW_CLASS
            Categories=AudioVideo;Video;Player;
            Keywords=movies;films;shows;tv;player;jellyfin;plex;
            $MANAGED
        """.trimIndent() + "\n"
    }

    /**
     * The Desktop Entry Specification's quoting for Exec, as the script has it: in double quotes; `"`, `` ` ``, `$` and `\` escaped
     * with a backslash, itself written twice because a string's own escapes are read first.
     */
    internal fun quote(value: String): String = "\"" + buildString {
        for (c in value) when (c) {
            '\\' -> append("\\\\\\\\")
            '"', '`', '$' -> append("\\\\").append(c)
            else -> append(c)
        }
    } + "\""

    /** Icon is a plain string: only a backslash is escaped. */
    internal fun plain(value: String): String = value.replace("\\", "\\\\")

    private fun write(target: Path, text: String) {
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temporary, text)
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** `$XDG_DATA_HOME`, or `~/.local/share`. */
    fun dataHome(): Path = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".local", "share")

    /** The packaged launcher's path, set by jpackage's launcher; null when not run from a package. */
    fun launcher(): Path? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let(Path::of)
}
